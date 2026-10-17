package com.example.sonder.platform.enforcement

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.sonder.BuildConfig
import com.example.sonder.data.repo.AccessEntry
import com.example.sonder.data.repo.AccessSessionStore
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.domain.BlockScope
import com.example.sonder.domain.FloatingHold
import com.example.sonder.domain.ForegroundSurface
import com.example.sonder.domain.ForegroundWatch
import com.example.sonder.domain.GateDecision
import com.example.sonder.domain.GateDecider
import com.example.sonder.domain.ShortsCatalog
import com.example.sonder.platform.accessibility.ActiveWindowContent
import com.example.sonder.platform.accessibility.ActiveWindowPackage
import com.example.sonder.platform.accessibility.AppCloser
import com.example.sonder.platform.accessibility.ForegroundWindows
import com.example.sonder.platform.accessibility.MediaPauser
import com.example.sonder.platform.accessibility.PipWindows
import com.example.sonder.platform.foreground.ForegroundResolver
import com.example.sonder.platform.notifications.Notifications
import com.example.sonder.platform.overlay.GateOverlayHost
import com.example.sonder.ui.gate.GateController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Turns foreground window events into blocker actions.
 *
 * The blocker is a service-owned system overlay window (see [GateOverlayHost]),
 * never an Activity: the detox app's task is never involved, so the blocked app
 * can never be reached through Recents, Back, or the detox app's own UI.
 *
 * Rules:
 *  - transient surfaces (shade, IME, keyguard, our own overlay window) never
 *    disturb the blocker
 *  - home/recents and the detox app itself release it
 *  - a real app is gated only if it is an enabled target whose bank is empty
 *  - any app that is not a blocked target releases the blocker
 *
 * Events are serialized on a single-threaded dispatcher and read the warm
 * in-memory snapshot, so a decision lands ~tens of milliseconds after the app
 * reaches the foreground.
 *
 * Releasing is the dangerous direction, so it is gated rather than corrected
 * afterwards: a window event that names Home (or this app) only releases the blocker
 * if the authoritative foreground does not disagree by naming a blocked app, because
 * Recents and Home transitions deliver their events in bursts and in whatever order
 * they please (see [isTrailingRelease]). Raising is the safe direction and is applied
 * immediately, both from events and from the re-check below.
 *
 * The event stream is also not guaranteed to arrive at all — see [onServiceConnected]
 * for the re-check that closes the rest of the gap.
 */
@Singleton
class EnforcementCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EnforcementRepository,
    private val overlayHost: GateOverlayHost,
    private val foregroundResolver: ForegroundResolver,
    private val controller: GateController,
    private val accessSessions: AccessSessionStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val ownPackage: String = context.packageName
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    /** The periodic foreground re-check; alive for as long as the service is. */
    private var watchJob: Job? = null

    /**
     * Whether [pkg] is an enabled target right now, from the warm cache.
     *
     * Exists for callers that have to filter an event stream *before* it reaches
     * [onForeground] — the content-change firehose is delivered for every window on the
     * device, so the one question worth asking of each event is whether its package is one
     * the user scoped. Non-suspending because it is answered from the cache the foreground
     * path already reads.
     */
    fun isEnabledTarget(pkg: String): Boolean = repository.enabledTarget(pkg) != null

    /**
     * Window-list evidence, supplied by the service while it is connected.
     *
     * The fields below are written from the accessibility-service main thread
     * ([onServiceConnected], [onScreenOff], [onServiceStopped]) and read and written from
     * the re-check's single-threaded dispatcher, with no lock between them. Volatile rather
     * than a holder object because each is read on its own and the pair is only ever used
     * to make one decision; confining them to the dispatcher would instead put a queue hop
     * on the service's own callback path, which is the path that has to be fast.
     */
    @Volatile private var foregroundWindows: ForegroundWindows? = null

    /**
     * Which app the accessibility window list says is in front, supplied by the service
     * while it is connected. Immediate where the usage-stats probe lags, and used only to
     * corroborate a release the re-check decided on that probe: see [releaseIsCorroborated].
     */
    @Volatile private var activeWindowPackage: ActiveWindowPackage? = null

    /**
     * The scoped-surface probe, supplied by the service while it is connected. Null when the
     * service is not, in which case no scoped target can recognise its surface and every one
     * of them passes — the reason a scoped target is safe to have enabled but never why it is
     * safe to be *the* gate: see [scopedSurfacePresent].
     */
    @Volatile private var activeWindowContent: ActiveWindowContent? = null

    /** Leave-a-gated-app, supplied by the service while it is connected. */
    @Volatile private var appCloser: AppCloser? = null

    /** Stop-what-is-playing, supplied by the service while it is connected. */
    @Volatile private var mediaPauser: MediaPauser? = null

    /** Is-this-app-floating, supplied by the service while it is connected. */
    @Volatile private var pipWindows: PipWindows? = null

    /** Collector for the gate's CLOSE control; alive for as long as the service is. */
    private var closeJob: Job? = null

    /** Package the last re-check pass decided for, and what it decided. */
    @Volatile private var watchedPackage: String? = null
    @Volatile private var watchedDecision: GateDecision? = null

    /**
     * Whether [pkg] still holds a won session, resuming it without billing time spent away.
     *
     * The screen guard is what stops an event that was already in flight when the screen went
     * off from re-entering a session that [onScreenOff] has just parked: without it, the
     * coroutine would resume the session on the way out and leave it running over the keyguard.
     */
    private suspend fun hasEntered(pkg: String, nowMillis: Long): Boolean {
        if (!screenIsInUse()) return false
        return when (accessSessions.enter(pkg, nowMillis)) {
            AccessEntry.DENIED -> false
            AccessEntry.ACTIVE -> true
            AccessEntry.RESUMED -> {
                // Back inside the grace: the clock restarts here, so the time that was spent
                // away is not billed when the next drain measures from this stamp.
                repository.beginUsage(pkg, nowMillis)
                billedPackage = pkg
                billedAtMillis = nowMillis
                true
            }
        }
    }

    /** Consecutive re-check passes that resolved away from every app. */
    @Volatile private var awayPasses = 0

    /**
     * Bumped whenever the watch starts or stops. A pass already in flight — parked in a
     * suspend lookup — re-reads it before acting and abandons, so a blocker can never be
     * raised after screen-off or after the service was unbound. `stopWatching` cancelling
     * the job only interrupts the loop at its next suspension point, and the pass that was
     * mid-flight is exactly the one that would raise the blocker over the keyguard.
     */
    @Volatile private var generation = 0

    /** Last foreground lookup, so both callers in one pass share a single usage-stats query. */
    private var probeAtMillis = 0L
    private var probePackage: String? = null

    /** Package and time of the last drain, so the hot path does not write per event. */
    private var billedPackage: String? = null
    private var billedAtMillis = 0L

    init {
        // The one event that used to be an alarm: the bank reaching zero. The notification
        // needs a Context, which the repository does not hold, so it is posted from here.
        repository.onBankDrained { pkg ->
            Notifications.notifyAccessExpired(context, pkg)
        }
    }

    /**
     * Start re-checking the real foreground for as long as the service is up.
     *
     * The event stream stays the fast path, and this closes what it cannot cover on its
     * own: an event that is never delivered (returning from Recents, an app-lock window
     * in front of the target), one that arrives after the fact, or a blocker window the
     * system has taken away underneath us. Without it, each of those leaves the app
     * uncovered until something unrelated happens to raise a fresh event — which is
     * exactly the "overlay comes seconds late, or sometimes never" behaviour.
     *
     * Idempotent, because the service announces every connection and every screen-on
     * through it.
     */
    fun onServiceConnected(
        windows: ForegroundWindows,
        activePackage: ActiveWindowPackage,
        content: ActiveWindowContent,
        appCloser: AppCloser,
        mediaPauser: MediaPauser,
        pipWindows: PipWindows,
    ) {
        foregroundWindows = windows
        activeWindowPackage = activePackage
        activeWindowContent = content
        this.appCloser = appCloser
        this.mediaPauser = mediaPauser
        this.pipWindows = pipWindows
        watchedPackage = null
        watchedDecision = null
        awayPasses = 0
        // A fresh connection is a fresh start, like a screen-on or a service stop. Anything
        // still held here describes a window the previous connection was covering, and this
        // one has raised nothing: a stale hold would refuse a launcher release for a full run
        // of confirming passes, which reads as the blocker sticking over Home.
        floatingHold = FloatingHold.NONE
        startWatching()
        startCloseRequests()
    }

    /**
     * Handle the gate's CLOSE control.
     *
     * The action belongs here rather than in the overlay host because it is the coordinator
     * that owns both ends: the host knows the window is up, and only the coordinator holds
     * the service capabilities. Starting the collector is idempotent, so the service
     * announcing a reconnection on every screen-on does not stack watchers — and a duplicate
     * would be the worse bug of the two, since two closes mean two Home actions and a kill
     * for a package the user has since left.
     */
    private fun startCloseRequests() {
        if (closeJob?.isActive == true) return
        closeJob = scope.launch {
            launch {
                controller.closeRequested.collect { pkg ->
                    // The user has asked to leave, so they have left: the session is parked and
                    // billed before the app goes, exactly as it is when any other app takes the
                    // screen. Without this the session stayed *active* — CLOSE APP is not a
                    // window event, so nothing else would have parked it — and a float the app
                    // leaves behind reads as a window the user is still inside and has paid for.
                    // It went unreported, the hold aged out, and the blocker came off the video
                    // it was covering until the absence grace expired and put it back: the
                    // strobe, from the one dismissal that never touched the session. [pauseAccess]
                    // is the same park-and-bill every other way out of a won app goes through.
                    //
                    // Caught rather than left to propagate, because this collector cannot be
                    // allowed to take its sibling down with it: the body reaches a Room
                    // transaction through [billForeground], this coroutine is a child of
                    // [closeJob], and a child that fails cancels its parent — so one database
                    // hiccup would also stop `unlocked` being collected, and a won hand could
                    // never open a gated app again. Failing soft keeps the pair independent; the
                    // dismissal and the close below are what the user asked for and run either
                    // way.
                    runCatching { pauseAccess(System.currentTimeMillis()) }
                        .onFailure { Log.w(TAG, "could not park the session on CLOSE", it) }
                    // No dispatch here, and the reason is the toggle's arithmetic rather than a
                    // missing fact. The gate sent its key when the blocker went up, so a key here
                    // would be the *second* for this episode — the one that starts the player
                    // back up — and the Home action immediately behind it is what hands a playing
                    // app a picture-in-picture window. The nudge meant to prevent the float
                    // builds it.
                    //
                    // Nothing here can tell whether that first key actually landed, which is why
                    // the decision is to send nothing rather than to send conditionally. The two
                    // candidates both fail: a clock cannot span the interval, since it is however
                    // long the user reads the panel, and the surface probe answers "is this app
                    // showing a short-form surface", not "is the player running" — it is built to
                    // keep answering yes when its own blocker is up (see `walkForMarker`'s
                    // coveredByUs), so it would say yes whatever the player is doing. Acting on
                    // either would dispatch on a guess, and the wrong guess is the resumed player.
                    // A missed pause costs a clip that plays to the instant Home lands, which the
                    // cover and the hold behind it already treat as survivable.
                    // Blocker first, in case the app ignores Home: the user must not be left
                    // staring at a wall over an app they asked to leave.
                    overlayHost.dismiss(reason = "closed by user: $pkg")
                    appCloser?.closePackage(pkg)
                }
            }
            // A won hand is the only thing that opens a gated app, so this is the only place
            // the entered marker is set. It is announced by the controller rather than
            // inferred from the bank, because the bank cannot tell a hand that was just won
            // from one won ten minutes ago — and only the former is a way in.
            launch {
                controller.unlocked.collect { grant ->
                    val now = System.currentTimeMillis()
                    val pkg = grant.packageName
                    accessSessions.grant(pkg, grant.durationMillis)
                    // The won window starts now, not at the hand that produced it: the table
                    // was on screen between the settlement and the tap, and none of that is
                    // time the user spent in the app. Restamping the bank makes the drain
                    // measure from this moment, so a win is never charged for the result
                    // screen it was read on.
                    repository.beginUsage(pkg, now)
                    billedPackage = pkg
                    billedAtMillis = now
                    // The decision is recorded as already made, and the blocker is taken down
                    // from here rather than by the host watching the same event: one collector
                    // owns the ordering, so a re-check pass can never see a gate that is down
                    // and re-decide from the bank before the grant is on the books.
                    watchedPackage = pkg
                    watchedDecision = GateDecision.GRANTED
                    // The hold goes with the blocker when it is *this* app's float the blocker
                    // was over: the user has just paid for that float, so the rule keeping the
                    // blocker on it has nothing left to hold. It has to be cleared here rather
                    // than left to age, because the pass cannot age it — the probe no longer
                    // reports a float of an app the user is inside, so a *blind* pass is all
                    // that arrives for it, [FloatingHold.Answer.Unknown] holds the counter by
                    // design, and a blind probe is the ordinary state while a Short plays. The
                    // pass would then return at the hold check below on every pass, and the app
                    // the user just won time in would never be billed again. A hold over some
                    // *other* app's float is deliberately untouched: that float is still unpaid.
                    if (floatingHold.coveredFor == pkg) floatingHold = FloatingHold.NONE
                    overlayHost.dismiss(reason = "access granted: $pkg")
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "ENTERED(pkg=$pkg duration=${grant.durationMillis})")
                    }
                }
            }
        }
    }

    private fun startWatching() {
        if (watchJob?.isActive == true) return
        generation++
        val mine = generation
        watchJob = scope.launch {
            while (isActive) {
                delay(WATCH_INTERVAL_MILLIS)
                try {
                    reconcile(mine)
                } catch (stopped: CancellationException) {
                    throw stopped
                } catch (failure: Throwable) {
                    // One bad pass — a repository or window-manager hiccup — must not end
                    // the loop: the next pass decides again from scratch.
                    Log.w(TAG, "foreground re-check failed", failure)
                }
            }
        }
    }

    private fun stopWatching() {
        generation++
        watchJob?.cancel()
        watchJob = null
    }

    /** Whether the pass that is running still belongs to the live watch. */
    private fun isCurrent(mine: Int): Boolean = mine == generation

    /**
     * One re-check pass: resolve the app that is really in the foreground and act on
     * what [ForegroundWatch] makes of it. Serialized with the event path, so a pass
     * never races a decision that an event is making at the same moment.
     */
    private suspend fun reconcile(mine: Int) {
        val now = System.currentTimeMillis()

        // Picture-in-picture is the one way out of the gate that does not go through it, so
        // it is settled first and takes precedence over everything below. A gated app that
        // was closed while a video was playing can come back as a small floating window,
        // which is not a window-state event for its activity and so is named by nothing else
        // in this class.
        if (screenIsInUse() && isCurrent(mine)) {
            val probe = probeFloat()
            // Asked again, and not because the answer might have changed in one assignment:
            // that probe parks this pass in `onServiceThread`'s latch, waiting on the very
            // thread the screen-off broadcast and service teardown are delivered on, so a
            // pass can come back from it into a screen that has gone off and a watch that has
            // been retired. Acting on that answer is what leaves the gate sitting over the
            // keyguard — the one thing [onScreenOff] exists to prevent. The guard is not the
            // only one: [cover] and [raiseGate] ask again before they raise anything.
            if (!isCurrent(mine) || !screenIsInUse()) return
            // One pass is not evidence about a float — see [FloatingHold] — so the answer is
            // aged through the rule rather than acted on directly. A float found now is
            // covered whatever the rule says, because covering is the safe direction, and a
            // pass that could not read the list is aged as [FloatingHold.Answer.Unknown] so it
            // holds what it has instead of being read as "the float is gone".
            val answer = probe.pkg?.let { FloatingHold.Answer.Found(it) }
                ?: if (probe.read) FloatingHold.Answer.None else FloatingHold.Answer.Unknown
            // Read before the ageing overwrites it, because it is what tells [coverFloating]
            // whether this float is the one the blocker is already over. The hold is the
            // episode: it stands for as long as the float does and is cleared outright by the
            // things that end one. See [coverFloating] for why the overlay window is not.
            val alreadyHeld = floatingHold.coveredFor == probe.pkg
            floatingHold = FloatingHold.afterPass(floatingHold, answer)
            if (!probe.read && probe.pkg == null) {
                if (BuildConfig.DEBUG) Log.d(TAG, "float probe unreadable; holding the blocker")
            }
            if (probe.pkg != null) {
                coverFloating(probe.pkg, mine, alreadyHeld = alreadyHeld)
                return
            }
            // A miss is not a release. This pass is the only one that runs every
            // [WATCH_INTERVAL_MILLIS], and while a hold is alive the float owns the screen:
            // the resolution below would read the launcher, call it HOME, and dismiss the
            // very blocker the hold was put up to keep — which is the strobe, arriving by
            // the one path that never consulted the hold. The pass falls through again on
            // its own once [FloatingHold] has run out of confirming passes.
            if (!FloatingHold.releaseAllowed(pkg = null, floating = probe.pkg, hold = floatingHold)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "release refused: a gated app is still floating")
                return
            }
        }

        val resolved = foregroundPackage(now)
        val observed = scopedAppInFront(resolved) ?: resolved ?: return

        // Our own package can only be resolved here through one of our Activities — the
        // overlay window is not an activity, so it leaves no resume behind. The class
        // name that classify() needs to tell those two apart is not available from a
        // package lookup, so the surface is settled here instead.
        val surface = if (observed == ownPackage) {
            ForegroundSurface.OWN
        } else {
            ForegroundSurface.classify(
                pkg = observed,
                ownPackage = ownPackage,
                isTarget = repository.enabledTarget(observed) != null,
            )
        }

        val step = ForegroundWatch.actionFor(
            surface = surface,
            settled = observed == watchedPackage && stillSettled(observed, now),
            awayPasses = awayPasses,
        )
        awayPasses = step.awayPasses

        when (step.action) {
            // A granted app the user is sitting inside emits no further window events, so
            // nothing else bills the time it is spending. Without this the bank would stand
            // still while the user never left, and every minute in the app would be free.
            // The re-check is the only thing that can see they are still there.
            ForegroundWatch.Action.IGNORE ->
                // What was last decided has to still hold before its grant is billed
                // against. It does not hold once the user has left the app: the won hand
                // went with them, and this is the pass that notices. Without the check the
                // user could walk out of a gated app, use everything else on the phone, and
                // come back to a blocker that never went up — the last decision still
                // reading GRANTED for an app they are no longer in.
                if (surface == ForegroundSurface.APP &&
                    watchedDecision == GateDecision.GRANTED &&
                    stillSettled(observed, now)
                ) {
                    billForeground(observed, now)
                }

            // Releasing is the dangerous direction from a pass as well as from an event.
            // The probe this pass decided on reads usage events, which the platform
            // batches, so right after an app comes up it can still name the app that was
            // in front before it — and a release decided on that answer takes down a
            // blocker that has only just gone up, leaving the app the user is looking at
            // uncovered. The event path already refuses a release the window list
            // disagrees with (see [releaseIsTrailing]); a pass has no event to check, so
            // it asks the same list directly.
            ForegroundWatch.Action.RELEASE ->
                if (releaseIsCorroborated(observed)) {
                    overlayHost.dismiss(reason = "foreground watch: $observed")
                    blockReleased(observed, reason = "foreground watch")
                } else if (BuildConfig.DEBUG) {
                    Log.d(TAG, "RELEASE_REFUSED(observed=$observed active=${activeWindow()})")
                }

            ForegroundWatch.Action.DECIDE ->
                if (screenIsInUse() && isCurrent(mine)) {
                    val decision = evaluate(pkg = observed, surface = surface, className = null)
                    watchedPackage = observed
                    watchedDecision = decision
                    blockState(decision, observed, "re-check")
                } else {
                    // Resolved but not in front of the user, or no longer the live watch:
                    // the last resumed activity is whatever is under the lock screen. Held
                    // state is dropped rather than kept, so the pass that follows the
                    // unlock — or the restart — decides from scratch.
                    watchedPackage = null
                    watchedDecision = null
                }
        }
    }

    /**
     * The foreground package, memoized briefly.
     *
     * A pass and the release probe that follows it inside the same pass would otherwise
     * each run their own `queryEvents` over a 60-second window — two binder calls per
     * 500 ms of service lifetime. One query answers both, because nothing can reach the
     * foreground and leave again inside the memo window.
     */
    private suspend fun foregroundPackage(nowMillis: Long): String? {
        if (probeAtMillis != 0L && nowMillis - probeAtMillis < FOREGROUND_MEMO_MILLIS) {
            return probePackage
        }
        val resolved = foregroundResolver.currentForegroundPackage(nowMillis)
        probeAtMillis = nowMillis
        probePackage = resolved
        return resolved
    }

    /**
     * Charge the app's bank for the time it has been in front, at most once per
     * [BILL_MIN_INTERVAL_MILLIS].
     *
     * The interval is the drain's resolution, not a permission: the repository bills from the
     * stamp left by the previous call, so time is never lost by skipping a beat — it is
     * simply charged in slightly larger steps. Writing on every window event would be a Room
     * round trip per event on the serialized decision dispatcher.
     */
    private suspend fun billForeground(
        pkg: String,
        nowMillis: Long,
        force: Boolean = false,
    ) {
        if (!force && pkg == billedPackage && nowMillis - billedAtMillis < BILL_MIN_INTERVAL_MILLIS) {
            return
        }
        billedPackage = pkg
        billedAtMillis = nowMillis
        // The session is what authorises use, so it is also the ceiling on the drain: the
        // final tick can never take more of the bank than the won window had left.
        val before = repository.cachedRemaining(pkg, nowMillis)
        val after = repository.billUsage(
            packageName = pkg,
            nowMillis = nowMillis,
            maxChargeMillis = accessSessions.remainingMillis(pkg),
        )
        accessSessions.consume(pkg, (before - after).coerceAtLeast(0L))
        if (after <= 0L) accessSessions.clear(pkg)
    }

    /**
     * Park every won session that is currently running, charging each its final interval.
     *
     * Called when the user leaves the gate for something that is not one more target: the
     * window each session covers is over, and the grace clock starts from here.
     */
    private suspend fun pauseAccess(nowMillis: Long, unlessPackage: String? = null) {
        val active = accessSessions.sessions.value.values
            .filter { it.awaySinceMillis == null && it.packageName != unlessPackage }
        if (active.isEmpty()) return
        accessSessions.leaveActive(nowMillis, except = unlessPackage)
        active.forEach { billForeground(it.packageName, nowMillis, force = true) }
    }

    /**
     * Whether a release-classified event must be refused, on one of two independent
     * pieces of evidence that the surface it names is not what the user is looking at.
     *
     * The window list is the first because it needs no permission and describes the
     * screen at the moment of the event. The usage-stats lookup is the second opinion:
     * it is a moment behind the events and needs usage access granted, so a device
     * without it would otherwise have no evidence at all — and a release decided with no
     * evidence is how a blocked app ends up usable again after a Recents round trip.
     */
    private suspend fun releaseIsTrailing(eventPkg: String, windowId: Int): Boolean {
        // The window list is the cheap evidence and it is decisive when it answers, so the
        // usage-stats lookup is skipped in that case: it is a binder query that needs a
        // permission, and it has nothing left to add to a window that is already known to
        // be covered. It stays as the second opinion for a device whose window list is not
        // readable, or where no window has focus at all.
        if (foregroundWindows?.isBehindAnotherWindow(windowId) == true) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "FOREGROUND_TRAILING(eventPkg=$eventPkg windowId=$windowId windowBehind=true)")
            }
            return true
        }

        val foreground = foregroundPackage(System.currentTimeMillis())
        val probeTrailing = foreground != null && ForegroundWatch.isTrailingRelease(
            eventPkg = eventPkg,
            foreground = foreground,
            ownPackage = ownPackage,
            foregroundBlocked = repository.enabledTarget(foreground) != null,
        )

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "FOREGROUND_TRAILING(eventPkg=$eventPkg windowId=$windowId " +
                    "windowBehind=false probe=$foreground refused=$probeTrailing)",
            )
        }
        return probeTrailing
    }

    /** Which package the accessibility window list says is in front, or null. */
    private fun activeWindow(): String? = activeWindowPackage?.activePackage()

    /**
     * A scoped target the accessibility window list says is in front, or null when it names
     * nothing this pass should decide on its behalf.
     *
     * The re-check's own probe reads usage events, which the platform batches, so a fast
     * round trip out to Recents and back can leave it naming the launcher for a while after
     * the user is already inside the app again — and the launcher's own event can land after
     * the app's, so the pass is looking at the older of two answers. For a whole-app target
     * that costs nothing: the app's window event names it and covers it. A scoped target is
     * reached *inside* its activity — Reels is a fragment of Instagram's main tab activity —
     * so no window event ever names its surface, and a pass that answers "launcher" never
     * looks for the surface at all. That is a target that stops working the first time the
     * user grazes Recents, with nothing in the event stream able to put it back.
     *
     * The window list is the immediate answer to the same question, and it is the same
     * evidence [releaseIsTrailing] and [releaseIsCorroborated] already lean on. It is trusted
     * here *only* for a scoped target, and only as the package to decide for: what happens
     * next is the surface probe, so if the app is not really on screen — or is on screen but
     * showing its feed — the pass still ends in PASS. A whole-app target is left to the probe
     * alone, because for it there is no second check and a window list that is a moment
     * stale in the other direction would raise the blocker over whatever the user is
     * actually looking at.
     *
     * @param resolved what that probe answered, which is what bounds the override: it is
     *   used only when the probe has no app of its own to decide for. Overriding it while it
     *   names a real app would decide for the app the window list named instead, and a PASS
     *   there dismisses a blocker that belongs to the app the user is actually in.
     */
    private fun scopedAppInFront(resolved: String?): String? {
        // Only when the probe has no app to decide for. A real app named by the probe is a
        // real switch — Chrome is in front, not Instagram — and the pass decides for it and
        // leaves the blocker where it is. Overriding there would decide for the app the
        // window list named instead, and a PASS for it takes down a blocker that belongs to
        // the app the user is actually in.
        val probeNamesAnApp = resolved != null &&
            ForegroundSurface.classify(
                pkg = resolved,
                ownPackage = ownPackage,
                isTarget = repository.enabledTarget(resolved) != null,
            ) == ForegroundSurface.APP
        if (probeNamesAnApp) return null

        val active = activeWindow() ?: return null
        if (active == ownPackage) return null
        if (repository.cachedBlockScope(active) != BlockScope.SHORTS_ONLY) return null
        return active.takeIf { repository.enabledTarget(it) != null }
    }

    /**
     * The enabled target playing on in a picture-in-picture window that has paid for nothing,
     * or null.
     *
     * Whether the user has won time decides whether a float is *reported*, not whether it is
     * looked for — see the body, where the two were once the same check and the app that was
     * actually floating was the one app never probed. A float belonging to an app with time in
     * the bank is something the user paid for, and is exactly what the gate promised them.
     */
    private fun probeFloat(): FloatProbe {
        if (!repository.isCacheReady()) return FloatProbe(pkg = null, read = false)
        val probe = pipWindows ?: return FloatProbe(pkg = null, read = false)
        // Asked once, and an empty answer is *readable*. The two early returns above are
        // "could not look" — no cache, no service — and answer Unknown so a hold survives them.
        // A target list with nothing in it is a different thing entirely: the walk ran and
        // there was nothing to walk, so the pass is settled and its answer is None. Left to fall
        // through, the loop below would never execute, `read` would stay at its initial false,
        // and the pass would be reported as an unreadable list — which freezes the miss counter
        // forever. The user can reach that: a float raises the blocker, then the target it
        // belongs to is soft-removed or disabled, and the blocker would have had no way down.
        val targets = repository.enabledPackages()
        if (targets.isEmpty()) return FloatProbe(pkg = null, read = true)
        // Every enabled target is probed, and the access session no longer decides *whether* to
        // ask. It used to: a target with an available session was skipped outright, so the app
        // that was actually floating — the one the user had just been gated by, which is the one
        // most likely to still hold a session — was the one app never measured. The others
        // answered "no small window of mine", those answers read as the list being legible, and
        // the hold aged out on the silence of every app except the one it was holding.
        //
        // The session still decides what the answer *means*, which is the part it was ever
        // entitled to: a float belonging to an app the user has won time in is the window they
        // paid for, and covering it would be the gate reneging on the hand it just paid out. So
        // a paid float is deliberately not reported — and, because nothing readable was found,
        // the pass ages the hold out and the blocker comes off, exactly as before.
        var read = false
        var blind = false
        var found: String? = null
        targets.forEach { pkg ->
            when (val answer = probe.pipWindowFor(pkg)) {
                is FloatingHold.Answer.Found -> {
                    read = true
                    if (found == null && !userIsInside(pkg)) {
                        found = answer.packageName
                    }
                }
                FloatingHold.Answer.None -> read = true
                // The window list could not be read for this app. It is not evidence that
                // anything is absent, so the caller must not act as if the float is gone.
                FloatingHold.Answer.Unknown -> blind = true
            }
        }
        return FloatProbe(pkg = found, read = read && !blind)
    }

    /**
     * Whether the user is *inside* [pkg] right now — actually in it, not merely entitled to it.
     *
     * This deliberately does not use [AccessSessionStore.isAvailableFor], which is the predicate
     * the gate uses to decide who may still be billed and includes [AccessSession]'s absence
     * grace. That grace is the whole point of the distinction here: it keeps a session alive for
     * a minute after the user leaves, which is right for billing — walking out for a moment must
     * not forfeit a won hand — and wrong for this question. CLOSE APP leaves the app, which marks
     * the session away, and the app's video keeps playing in the float it left behind; read
     * through the grace, that float is a window the user *paid for*, so it went unreported, the
     * hold aged out on the silence, and the blocker came off the very video it was covering —
     * then went straight back up when the grace expired, which is the flash.
     *
     * A session with no absence is one the user has not left, so this is the same notion of
     * "in the app" that [AccessEntry.ACTIVE] carries out of [AccessSessionStore.enter] — and
     * the `== true` is load-bearing rather than style. A package with **no session at all** is
     * the ordinary state of a target the user has not won time in: the bank was spent, or a
     * hand was never won, and [AccessSessionStore.grant] is the only thing that ever creates
     * one. Writing this as `sessions[pkg]?.awaySinceMillis == null` reads that missing session
     * as "not away" and so as "the user is inside" — which is the exact inversion of the
     * intent, and it lands on the one app that matters: a gated app that paid nothing leaves a
     * float behind, and that is precisely the float this rule exists to report. [AccessEntry]
     * agrees — `enter` on a package with no session answers [AccessEntry.DENIED], never ACTIVE.
     */
    private fun userIsInside(pkg: String): Boolean {
        val session = accessSessions.sessions.value[pkg] ?: return false
        return session.awaySinceMillis == null && session.remainingMillis > 0L
    }

    /**
     * The target whose float the blocker should be over, or null when no target has one.
     *
     * Null is the *only* answer this can give when a pass could not read the window list — the
     * [FloatProbe.read] bit that distinguishes that case is dropped here, because the callers
     * of this form take a package and nothing else. Every one of them then asks
     * [FloatingHold.releaseAllowed], which refuses on a standing hold, so a blind pass cannot
     * release through this path: it arrives as "nothing found" against a hold that is still
     * up. See [FloatProbe] for the form that does carry the distinction.
     */
    private fun pipTargetToCover(): String? = probeFloat().pkg

    /**
     * One float probe against every target: the target to cover, and whether the pass settled.
     *
     * [read] is false when any enabled target answered [FloatingHold.Answer.Unknown] — the class
     * of pass that arrives exactly while a Short is playing, because the service thread the
     * probe parks on is the one the playing app saturates. Reading such a pass as "no float" is
     * the strobe the hold exists to stop, so it is reported as unreadable and the hold is left
     * standing. A target that *was* probed and had no small window does not rescue the pass: the
     * app that had to guess is the app that could be the float.
     */
    private data class FloatProbe(val pkg: String?, val read: Boolean)

    /**
     * The float the blocker was raised over and the run of passes since one found it. Aged
     * by [FloatingHold], which is where the reasoning lives.
     *
     * One value rather than a package beside a counter: [onScreenOff] and [onServiceStopped]
     * clear it from the accessibility service's own thread while a pass on the dispatcher is
     * ageing it, and two fields cannot be swapped between threads as one fact — a reader that
     * saw the new package with the old run of misses, or a clear that landed between the two
     * writes, would end the next hold early or keep a dropped one alive.
     */
    @Volatile private var floatingHold: FloatingHold.Hold = FloatingHold.NONE

    /**
     * Pause the float and put the blocker over it.
     *
     * Both halves matter and neither is redundant: the pause stops the audio the opaque
     * window cannot, and the window stops the picture. The blocker is raised for the floating
     * app rather than for whatever is in front, because it is the floating app the user is
     * watching — the panel then offers the hand that would earn its time back.
     *
     * [mine] is the watch this cover belongs to, and it is asked one last time here because
     * the caller's own check is now behind it: the raising goes through
     * [GateOverlayHost.showGate], which only posts to the main looper, so a screen-off that
     * lands in between would otherwise post its dismissal first and be overtaken by the
     * window this call creates — a blocker left composed over the keyguard, with nothing on
     * screen classified as release-worthy to take it down.
     *
     * [alreadyHeld] is the caller's answer to "was the hold already over this float", read before
     * this pass aged the hold, and together with the blocker window it decides the pause. Two
     * facts rather than one, because each covers the case the other cannot:
     *
     *  - the *window* is what notices a raise that has not become a float yet. The gate branch
     *    dispatches its own key when the blocker goes up, and a hold is not started until a pass
     *    finds the float — so on the pass that first finds one, the hold is still empty and the
     *    only record of that earlier key is the blocker standing over the package. Conditioned on
     *    the hold alone, this would send the *even* dispatch and start the player back up;
     *  - the *hold* is what survives the window being destroyed under a live float. A
     *    configuration change tears the overlay off, and [GateOverlayHost] drops its record when
     *    that happens, while the float it was over keeps playing in its own window. Conditioned
     *    on the window alone — which is what this was — that pass read "no blocker is up for this
     *    app", sent the key again, and resumed the float behind its own blocker.
     *
     * The hold does not have the first failure and the window does not have the second, so the
     * pause is sent only when neither says a key has already gone out for this episode. A second
     * float over the same package still gets its own dispatch: both facts are cleared by the
     * things that end an episode — screen-off, service stop, a fresh connection, a satisfied
     * grant, a rebuild of the blocker.
     */
    private fun coverFloating(pkg: String, mine: Int, alreadyHeld: Boolean) {
        if (!isCurrent(mine) || !screenIsInUse()) return
        // The pause is sent once per float, not once per pass. This is called for as long as the
        // float is found — a PiP window persists, so that is every [WATCH_INTERVAL_MILLIS] —
        // and the media key is a *toggle*, not the dedicated pause it used to be. Sent every
        // pass it would be a pause, a resume, a pause: the debounce only slows the alternation
        // to one move per [MEDIA_PAUSE_DEBOUNCE_MILLIS], so a covered float would have its audio
        // flipping on and off for as long as it stayed covered, which is worse than either
        // extreme.
        if (!alreadyHeld && overlayHost.shownForPackage != pkg) mediaPauser?.pauseMedia()
        val label = repository.enabledTarget(pkg)?.label?.takeIf { it.isNotBlank() }
            ?: pkg.substringAfterLast('.').uppercase()
        overlayHost.showGate(pkg, label)
        watchedPackage = pkg
        watchedDecision = GateDecision.GATE
        // The counter belongs to the position this pass was watching, and that position has
        // just ended: Home is where the float came from, so the run of away-passes that got
        // the user here is normally already at [ForegroundWatch.CONFIRMING_PASSES]. Left
        // standing it would let the first pass that misses the float release the blocker
        // outright, which is the flash this cover was raised to end.
        awayPasses = 0
        if (BuildConfig.DEBUG) Log.d(TAG, "PIP_COVERED(pkg=$pkg)")
    }

    /**
     * Whether the accessibility window list agrees that [observed] is really the app on
     * screen, so a release the re-check decided for it can be applied.
     *
     * Only an explicit disagreement refuses. A missing answer — no connected service, no
     * focused window, an IME or system window holding focus — leaves the release as it was
     * before this check existed, because refusing on no evidence would strand the blocker
     * over Home on the devices where the list cannot be read, which is the failure the
     * re-check release exists to prevent.
     */
    private fun releaseIsCorroborated(observed: String): Boolean {
        val active = activeWindow() ?: return true
        return active == observed
    }

    /** One line per applied state, so a logcat trace shows which event won a transition. */
    private fun blockState(decision: GateDecision, pkg: String, reason: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(
            TAG,
            "BLOCK_STATE_CHANGED(locked=${decision == GateDecision.GATE} pkg=$pkg " +
                "decision=$decision $reason)",
        )
    }

    /** A release starts the return grace after charging the final foreground interval. */
    private suspend fun blockReleased(pkg: String, reason: String) {
        pauseAccess(System.currentTimeMillis())
        if (BuildConfig.DEBUG) Log.d(TAG, "BLOCK_STATE_CHANGED(locked=false pkg=$pkg $reason)")
    }

    /**
     * Whether the screen is on and past the keyguard. The re-check resolves the last
     * *resumed* activity, which stays the app under the lock screen for as long as the
     * screen is locked — so without this a pass would raise the blocker over the
     * keyguard, which the blocker must never do (see [onScreenOff]).
     */
    private fun screenIsInUse(): Boolean =
        powerManager.isInteractive && !keyguardManager.isKeyguardLocked

    /**
     * Whether the app the caller is asking about is showing the surface its scope gates.
     *
     * For [BlockScope.WHOLE_APP] this is vacuously true — every window of the app is the
     * surface. For [BlockScope.SHORTS_ONLY] it is the probe, and it is the one place in the
     * enforcement path that reads anything but a package name.
     *
     * Deliberately not memoized. The 400ms memo on [foregroundPackage] exists because that
     * lookup is a 60-second `queryEvents` binder call made twice in one pass; this one is a
     * bounded walk of a tree the service already holds, and paying for it twice rather than
     * delaying a gate is the right trade for a feature whose whole value is that the blocker
     * lands *while* the user is watching a reel. A memo would also hold a stale "not the
     * surface" across the exact transition this exists to catch.
     *
     * A missing capability answers false, which passes the app. That is the safe direction —
     * the alternative is gating an app on no evidence — but it also means a scoped target
     * degrades to "not gated at all" rather than to "gated everywhere" without a connected
     * service, which is why the first is the thing to check when one appears not to work.
     */
    private fun scopedSurfacePresent(pkg: String, scope: BlockScope): Boolean {
        if (scope != BlockScope.SHORTS_ONLY) return true
        return shortFormSurfacePresent(pkg)
    }

    /**
     * Whether [pkg] is showing one of its catalogue's short-form surfaces, whatever its
     * scope.
     *
     * Used for the pause as well as for the scope: a whole-app YouTube target showing Shorts
     * is a target whose video must stop under the blocker, and answering that from the scope
     * would leave the one case the user actually reported still playing.
     */
    private fun shortFormSurfacePresent(pkg: String): Boolean {
        val markers = ShortsCatalog.markersFor(pkg)
        if (markers.isEmpty()) return false
        return activeWindowContent?.showsMarkers(pkg, markers) == true
    }

    /**
     * Whether what the last re-check pass decided for [pkg] still holds, so the app does
     * not have to be decided again.
     *
     * A gate is settled only while its window is genuinely up, so a blocker the system
     * removed is raised again on the next pass. A grant is settled only while the bank still
     * holds time: the drain can empty it mid-use, and the blocker has to come up then rather
     * than waiting for the user to leave and come back. A passing app is never settled —
     * re-deciding is what lets a target enabled while its app is open start being blocked,
     * and it covers the enforcement cache still being cold on the first passes.
     *
     * A [BlockScope.SHORTS_ONLY] gate is settled like any other, which it was not at first.
     * The exception existed to notice the user leaving Reels for the Instagram feed, where no
     * activity is resumed and so no window event is delivered. That cannot be noticed from
     * here either: the scoped-surface probe is blind while our own blocker covers the app
     * (see [ActiveWindowContent]), so re-deciding each pass only re-reads the same "still on
     * the surface" answer and logs it twice a second. Leaving the app is what brings a scoped
     * blocker down, and that arrives as a window event.
     */
    private suspend fun stillSettled(pkg: String, nowMillis: Long): Boolean = when (watchedDecision) {
        GateDecision.PASS -> false

        GateDecision.GRANTED ->
            repository.cachedRemaining(pkg, nowMillis) > 0L && hasEntered(pkg, nowMillis)

        GateDecision.GATE -> overlayHost.shownForPackage == pkg

        // The wall is settled only while the bank is still spent. It has a countdown on it
        // that promises the refill, and midnight can arrive while the user is still staring
        // at it — a wall left settled across the refill would sit there past the moment it
        // announced, so the pass that sees time in the bank again re-decides and takes it
        // down.
        GateDecision.LOCKED ->
            repository.cachedRemaining(pkg) <= 0L && overlayHost.shownForPackage == pkg

        null -> false
    }

    fun onForeground(
        pkg: String,
        className: String? = null,
        windowId: Int = 0,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "FOREGROUND_CHANGED(pkg=$pkg class=$className window=$windowId)")
        }
        scope.launch {
            // Classified here rather than in the service. Whether the package is an enabled
            // target is an input to the classification — it is what stops the launcher and
            // IME hints from exempting a target the user chose to gate — and that answer
            // lives on the enforcement cache.
            val surface = ForegroundSurface.classify(
                pkg = pkg,
                ownPackage = ownPackage,
                className = className,
                isTarget = repository.enabledTarget(pkg) != null,
            )
            when (surface) {
                // Our own overlay window reports our package with a non-app class
                // name; ignoring it is what stops the blocker from dismissing itself.
                ForegroundSurface.TRANSIENT -> return@launch

                ForegroundSurface.HOME, ForegroundSurface.OWN -> {
                    // A floating window has no event of its own, so the way Home lands while
                    // one is up is as a release — and honouring it is how a gated app ends up
                    // playing on over the launcher. The pass that follows would raise the
                    // blocker again, and the two answers together are a flash loop; so the
                    // release is refused here for the same reason the pass refuses it.
                    if (!FloatingHold.releaseAllowed(
                            pkg = null,
                            floating = pipTargetToCover(),
                            hold = floatingHold,
                        )
                    ) {
                        return@launch
                    }
                    // Recents and Home deliver their window events in bursts, and the
                    // launcher's own event can land *after* the blocked app's. Releasing
                    // on that one event is what uncovered an app the user was looking at.
                    if (releaseIsTrailing(pkg, windowId)) return@launch
                    overlayHost.dismiss(reason = "surface=$surface pkg=$pkg")
                    blockReleased(pkg, reason = "surface=$surface")
                    return@launch
                }

                ForegroundSurface.APP -> Unit
            }

            val decision = evaluate(pkg = pkg, surface = surface, className = className)
            blockState(decision, pkg, reason = "event")
        }
    }

    /**
     * The single decision path, shared by live events and the foreground re-check.
     * Returns what it applied, so a caller that has to remember the outcome — the
     * re-check does — does not have to derive it again.
     */
    private suspend fun evaluate(
        pkg: String,
        surface: ForegroundSurface,
        className: String?,
    ): GateDecision {
        val now = System.currentTimeMillis()
        pauseAccess(now, unlessPackage = pkg)

        val target = repository.enabledTarget(pkg)
        val scope = repository.cachedBlockScope(pkg)
        val scopedSurface = scopedSurfacePresent(pkg, scope)
        val decision = GateDecider.decide(
            targetEnabled = target != null,
            bankMillis = repository.cachedRemaining(pkg, now),
            blockScope = scope,
            scopedSurfacePresent = scopedSurface,
            entered = hasEntered(pkg, now),
        )

        val label = target?.label?.takeIf { it.isNotBlank() }
            ?: pkg.substringAfterLast('.').uppercase()

        when (decision) {
            GateDecision.PASS ->
                // A cache that has not answered yet says "no targets" about every package,
                // and releasing on that answer is how a blocked app gets through in the
                // first moments after the service starts. Raising is the safe direction and
                // is never gated this way. A floating window is the same kind of refusal:
                // a PASS for some other app must not uncover the float.
                if (!repository.isCacheReady()) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "cache still warming; holding the blocker rather than releasing $pkg")
                    }
                } else if (!FloatingHold.releaseAllowed(
                        pkg = null,
                        floating = pipTargetToCover(),
                        hold = floatingHold,
                    )
                ) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "release refused: a gated app is still floating")
                    }
                } else {
                    overlayHost.dismiss(reason = "not a target: $pkg")
                    blockReleased(pkg, reason = "not a target")
                }

            GateDecision.GRANTED -> {
                // Billing before the release, so the time this event was raised for is
                // charged to the session that is about to run rather than to the one after.
                billForeground(pkg, now)
                // Granted time is this app's, not the screen's. A float belonging to some
                // *other* drained app is the same refusal the PASS branch makes: releasing
                // on this decision would take the blocker off the app the user is watching
                // and the next pass would put it back — the strobe again, from the one
                // dismissal that never asked about a float. [pkg]'s own float is the case
                // where releasing is the whole point, and the hold goes down with it.
                val floating = pipTargetToCover()
                val hold = floatingHold
                if (!FloatingHold.releaseAllowed(pkg = pkg, floating = floating, hold = hold)) {
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "release refused: ${floating ?: hold.coveredFor} is still floating")
                    }
                } else {
                    floatingHold = FloatingHold.NONE
                    overlayHost.dismiss(reason = "granted: $pkg")
                }
            }

            // The bank is spent: there is nothing to stake and nothing to play for, so the
            // panel is the wall. The pause is the same as the table's and for the same
            // reason — a clip behind an opaque window keeps advancing, and a wall that lets
            // the reel play on is not a wall.
            GateDecision.GATE,
            GateDecision.LOCKED,
            -> {
                if (shortFormSurfacePresent(pkg)) mediaPauser?.pauseMedia()
                overlayHost.showGate(pkg, label)
            }
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "pkg=$pkg surface=$surface class=$className scope=$scope " +
                    "scopedSurface=$scopedSurface bank=${repository.cachedRemaining(pkg, now)} " +
                    "decision=$decision blocker=${overlayHost.shownForPackage}",
            )
        }
        return decision
    }

    /**
     * Screen off: never leave the blocker covering the keyguard, and nothing on screen
     * to re-check until it comes back.
     */
    fun onScreenOff() {
        stopWatching()
        // Nothing is on screen to hold a blocker over, and the watch that ages the hold out
        // is the thing being stopped: left set, it would outlive the float it was holding.
        floatingHold = FloatingHold.NONE
        val now = System.currentTimeMillis()
        // Parked before the billing is queued, so a window event already in flight cannot find
        // the session still running; [hasEntered] refuses to resume it while the screen is off.
        val running = accessSessions.sessions.value.values.filter { it.awaySinceMillis == null }
        accessSessions.leaveActive(now)
        running.forEach { session ->
            scope.launch { billForeground(session.packageName, now, force = true) }
        }
        overlayHost.dismiss(reason = "screen off")
    }

    /** Screen on: re-checking resumes; the keyguard holds it off the lock screen. */
    fun onScreenOn() {
        startWatching()
    }

    /** Service torn down / unbound: the blocker must not outlive it. */
    fun onServiceStopped() {
        stopWatching()
        closeJob?.cancel()
        closeJob = null
        foregroundWindows = null
        activeWindowPackage = null
        activeWindowContent = null
        appCloser = null
        mediaPauser = null
        pipWindows = null
        watchedPackage = null
        watchedDecision = null
        accessSessions.clear()
        floatingHold = FloatingHold.NONE
        awayPasses = 0
        overlayHost.dismiss(reason = "service stopped")
    }

    private companion object {
        const val TAG = "SonderGate"

        /**
         * How often the foreground is re-checked. Fast enough that a gate the event
         * stream lost lands inside the app's own launch animation, slow enough to be a
         * negligible cost while the service is idle.
         */
        const val WATCH_INTERVAL_MILLIS = 500L

        /**
         * How long one foreground lookup answers for. Longer than the interval between two
         * callers inside a single pass, far shorter than any real app switch.
         */
        const val FOREGROUND_MEMO_MILLIS = 400L

        /**
         * How often the bank is billed for the app on screen.
         *
         * The resolution of the drain, not a permission: the repository measures from the
         * stamp the previous call left, so a skipped beat is charged late rather than lost.
         * Billing per window event would be a Room round trip per event on the serialized
         * decision dispatcher.
         */
        const val BILL_MIN_INTERVAL_MILLIS = 5_000L
    }
}
