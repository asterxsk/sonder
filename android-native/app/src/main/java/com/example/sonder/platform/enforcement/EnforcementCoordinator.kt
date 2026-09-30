package com.example.sonder.platform.enforcement

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.sonder.BuildConfig
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.BlockScope
import com.example.sonder.domain.ForegroundSurface
import com.example.sonder.domain.ForegroundWatch
import com.example.sonder.domain.GateDecision
import com.example.sonder.domain.GateDecider
import com.example.sonder.domain.ShortsCatalog
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot
import com.example.sonder.platform.accessibility.ActiveWindowContent
import com.example.sonder.platform.accessibility.ActiveWindowPackage
import com.example.sonder.platform.accessibility.AppCloser
import com.example.sonder.platform.accessibility.ForegroundWindows
import com.example.sonder.platform.foreground.ForegroundResolver
import com.example.sonder.platform.overlay.GateOverlayHost
import com.example.sonder.platform.scheduling.GrantExpiryScheduler
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
 *  - a real app is gated only if it is an enabled target without an active grant
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
    private val expiryScheduler: GrantExpiryScheduler,
    private val controller: GateController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val ownPackage: String = context.packageName
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    /** The periodic foreground re-check; alive for as long as the service is. */
    private var watchJob: Job? = null

    /**
     * Window-list evidence, supplied by the service while it is connected.
     *
     * The four fields below are written from the accessibility-service main thread
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

    /** Collector for the gate's CLOSE control; alive for as long as the service is. */
    private var closeJob: Job? = null

    /** Package the last re-check pass decided for, and what it decided. */
    @Volatile private var watchedPackage: String? = null
    @Volatile private var watchedDecision: GateDecision? = null

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

    /** Package and time of the last last-seen write, so the hot path does not write per event. */
    private var lastSeenPackage: String? = null
    private var lastSeenAtMillis = 0L

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
    ) {
        foregroundWindows = windows
        activeWindowPackage = activePackage
        activeWindowContent = content
        this.appCloser = appCloser
        watchedPackage = null
        watchedDecision = null
        awayPasses = 0
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
            controller.closeRequested.collect { pkg ->
                // Blocker first, in case the app ignores Home: the user must not be left
                // staring at a wall over an app they asked to leave.
                overlayHost.dismiss(reason = "closed by user: $pkg")
                appCloser?.closePackage(pkg)
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
        val observed = foregroundPackage(now) ?: return

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
            settled = observed == watchedPackage && stillSettled(observed),
            awayPasses = awayPasses,
        )
        awayPasses = step.awayPasses

        when (step.action) {
            // A granted app the user is sitting inside emits no further window events, so
            // nothing else refreshes the last-seen stamp. Without this the stamp ages past
            // the absence window while the user never left, and the next event revokes a
            // grant for an absence that did not happen. The re-check is the only thing
            // that can see they are still there.
            ForegroundWatch.Action.IGNORE ->
                if (surface == ForegroundSurface.APP && watchedDecision == GateDecision.GRANTED) {
                    touchLastSeen(observed, now)
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

    /** Refresh the last-seen stamp at most once per [LAST_SEEN_MIN_INTERVAL_MILLIS]. */
    private suspend fun touchLastSeen(pkg: String, nowMillis: Long) {
        if (pkg == lastSeenPackage && nowMillis - lastSeenAtMillis < LAST_SEEN_MIN_INTERVAL_MILLIS) {
            return
        }
        lastSeenPackage = pkg
        lastSeenAtMillis = nowMillis
        repository.recordLastSeen(pkg, nowMillis)
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
        val locked = decision == GateDecision.GATE ||
            decision == GateDecision.REVOKE ||
            decision == GateDecision.LOCKOUT
        Log.d(TAG, "BLOCK_STATE_CHANGED(locked=$locked pkg=$pkg decision=$decision $reason)")
    }

    /** A release that was applied rather than refused; the blocker is down for it. */
    private fun blockReleased(pkg: String, reason: String) {
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
        val markers = ShortsCatalog.markersFor(pkg)
        if (markers.isEmpty()) return false
        return activeWindowContent?.showsMarkers(pkg, markers) == true
    }

    /**
     * Whether what the last re-check pass decided for [pkg] still holds, so the app does
     * not have to be decided again.
     *
     * A gate is settled only while its window is genuinely up, so a blocker the system
     * removed is raised again on the next pass. A grant is settled only while it is still
     * live: a grant that lapses or is revoked mid-use brings the blocker up without
     * waiting for the user to leave and come back. A passing app is never settled —
     * re-deciding is what lets a target enabled while its app is open start being
     * blocked, and it covers the enforcement cache still being cold on the first passes.
     *
     * A [BlockScope.SHORTS_ONLY] gate is settled like any other, which it was not at first.
     * The exception existed to notice the user leaving Reels for the Instagram feed, where no
     * activity is resumed and so no window event is delivered. That cannot be noticed from
     * here either: the scoped-surface probe is blind while our own blocker covers the app
     * (see [ActiveWindowContent]), so re-deciding each pass only re-reads the same "still on
     * the surface" answer and logs it twice a second. Leaving the app is what brings a scoped
     * blocker down, and that arrives as a window event.
     */
    private fun stillSettled(pkg: String): Boolean = when (watchedDecision) {
        GateDecision.PASS -> false

        GateDecision.GRANTED ->
            repository.cachedGrant(pkg)?.endAtMillis?.let { it > System.currentTimeMillis() } == true

        GateDecision.GATE, GateDecision.REVOKE -> overlayHost.shownForPackage == pkg

        // A lockout is settled only while its deadline is still ahead. Past it the wait has
        // been served and the pass has to decide again, or the panel sits over the app
        // against a timer that has already run out and never hands it back.
        GateDecision.LOCKOUT ->
            overlayHost.shownForPackage == pkg && lockoutIsRunning(pkg)

        null -> false
    }

    /** Whether a live lockout row still has time on it. */
    private fun lockoutIsRunning(pkg: String): Boolean =
        repository.cachedLockout(pkg)?.untilMillis?.let { it > System.currentTimeMillis() } == true

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

        // Housekeeping: purge an expired grant row so enforcement resumes.
        repository.cachedGrant(pkg)?.takeIf { it.endAtMillis <= now }?.let {
            repository.revokeGrant(pkg)
        }
        // A debt lockout that has run out has been served, so the debt goes with it —
        // otherwise the app keeps gating against a timer that is already over. The caches
        // catch up asynchronously, so this pass decides on the cleared values itself.
        val debtServed = repository.cachedLockout(pkg)
            ?.takeIf { (it.reason ?: EnforcementRepository.LOCKOUT_REASON_DEBT) == EnforcementRepository.LOCKOUT_REASON_DEBT }
            ?.takeIf { it.untilMillis <= now }
            ?.let { repository.serveDebtIfLockoutElapsed(pkg, now) }
            ?: false

        val target = repository.enabledTarget(pkg)
        val debtMillis = if (debtServed) 0L else repository.cachedDebt(pkg)
        val debtAtCap = AccessPolicy.isDebtAtCap(debtMillis, repository.cachedMaxDebt(pkg))
        val scope = repository.cachedBlockScope(pkg)
        val scopedSurface = scopedSurfacePresent(pkg, scope)
        val decision = GateDecider.decide(
            targetEnabled = target != null,
            grant = repository.cachedGrant(pkg)?.let {
                GrantSnapshot(it.packageName, it.endAtMillis, it.lastSeenMillis)
            },
            lockout = if (debtServed) {
                null
            } else {
                repository.cachedLockout(pkg)?.let {
                    LockoutSnapshot(it.packageName, it.untilMillis, it.reason)
                }
            },
            nowMillis = now,
            debtAtCap = debtAtCap,
            // The target's own absence window rather than the global default: the two
            // paths used to disagree, so a per-app override changed what the repository's
            // own check said and nothing about what the live gate did.
            absenceRevokeMillis = repository.cachedAbsenceRevoke(pkg),
            blockScope = scope,
            scopedSurfacePresent = scopedSurface,
        )

        val label = target?.label?.takeIf { it.isNotBlank() }
            ?: pkg.substringAfterLast('.').uppercase()

        when (decision) {
            GateDecision.PASS ->
                // A cache that has not answered yet says "no grants, no targets" about
                // every package, and releasing on that answer is how a blocked app gets
                // through in the first moments after the service starts. Raising is the
                // safe direction and is never gated this way.
                if (repository.isCacheReady()) {
                    overlayHost.dismiss(reason = "not a target: $pkg")
                } else if (BuildConfig.DEBUG) {
                    Log.d(TAG, "cache still warming; holding the blocker rather than releasing $pkg")
                }

            GateDecision.GRANTED -> {
                touchLastSeen(pkg, now)
                overlayHost.dismiss(reason = "granted: $pkg")
            }

            GateDecision.REVOKE -> {
                repository.revokeGrant(pkg)
                // The alarm outlives the grant otherwise, and fires later to announce an
                // expiry that has already happened.
                expiryScheduler.cancelExpiry(pkg)
                overlayHost.showGate(pkg, label)
            }

            GateDecision.LOCKOUT -> overlayHost.showLockout(
                pkg = pkg,
                label = label,
                // The deadline, not the time left: the panel counts down against a clock,
                // and the coordinator can be called again a moment later with a slightly
                // different remainder, which would move the deadline on every event.
                //
                // A debt at the ceiling waits out its lockout; if that row is somehow
                // already gone, the debt itself is the time still owed.
                untilMillis = repository.cachedLockout(pkg)
                    ?.takeIf { it.untilMillis > now }
                    ?.untilMillis
                    ?: (now + debtMillis),
            )

            GateDecision.GATE -> overlayHost.showGate(pkg, label)
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "pkg=$pkg surface=$surface class=$className scope=$scope " +
                    "scopedSurface=$scopedSurface decision=$decision " +
                    "blocker=${overlayHost.shownForPackage}",
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
        watchedPackage = null
        watchedDecision = null
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
         * How often a last-seen stamp is written for the app on screen. The stamp only has
         * to stay inside the absence window — 20 seconds in debug, 60 in release — so a
         * write per window event is pure cost on the serialized decision dispatcher.
         */
        const val LAST_SEEN_MIN_INTERVAL_MILLIS = 5_000L
    }
}
