package com.example.sonder.platform.enforcement

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.sonder.BuildConfig
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.ForegroundSurface
import com.example.sonder.domain.ForegroundWatch
import com.example.sonder.domain.GateDecision
import com.example.sonder.domain.GateDecider
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot
import com.example.sonder.platform.accessibility.ForegroundWindows
import com.example.sonder.platform.foreground.ForegroundResolver
import com.example.sonder.platform.overlay.GateOverlayHost
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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val ownPackage: String = context.packageName
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val keyguardManager =
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

    /** The periodic foreground re-check; alive for as long as the service is. */
    private var watchJob: Job? = null

    /** Window-list evidence, supplied by the service while it is connected. */
    private var foregroundWindows: ForegroundWindows? = null

    /** Package the last re-check pass decided for, and what it decided. */
    private var watchedPackage: String? = null
    private var watchedDecision: GateDecision? = null

    /** Consecutive re-check passes that resolved away from every app. */
    private var awayPasses = 0

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
    fun onServiceConnected(windows: ForegroundWindows) {
        foregroundWindows = windows
        watchedPackage = null
        watchedDecision = null
        awayPasses = 0
        startWatching()
    }

    private fun startWatching() {
        if (watchJob?.isActive == true) return
        watchJob = scope.launch {
            while (isActive) {
                delay(WATCH_INTERVAL_MILLIS)
                try {
                    reconcile()
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
        watchJob?.cancel()
        watchJob = null
    }

    /**
     * One re-check pass: resolve the app that is really in the foreground and act on
     * what [ForegroundWatch] makes of it. Serialized with the event path, so a pass
     * never races a decision that an event is making at the same moment.
     */
    private suspend fun reconcile() {
        val observed = foregroundResolver.currentForegroundPackage() ?: return

        // Our own package can only be resolved here through one of our Activities — the
        // overlay window is not an activity, so it leaves no resume behind. The class
        // name that classify() needs to tell those two apart is not available from a
        // package lookup, so the surface is settled here instead.
        val surface = if (observed == ownPackage) {
            ForegroundSurface.OWN
        } else {
            ForegroundSurface.classify(observed, ownPackage)
        }

        val step = ForegroundWatch.actionFor(
            surface = surface,
            settled = observed == watchedPackage && stillSettled(observed),
            awayPasses = awayPasses,
        )
        awayPasses = step.awayPasses

        when (step.action) {
            ForegroundWatch.Action.IGNORE -> Unit

            ForegroundWatch.Action.RELEASE ->
                overlayHost.dismiss(reason = "foreground watch: $observed")

            ForegroundWatch.Action.DECIDE ->
                if (screenIsInUse()) {
                    val decision = evaluate(pkg = observed, surface = surface, className = null)
                    watchedPackage = observed
                    watchedDecision = decision
                    blockState(decision, observed, "re-check")
                } else {
                    // Resolved but not in front of the user: the last resumed activity is
                    // whatever is under the lock screen. Held state is dropped rather than
                    // kept, so the pass that follows the unlock decides from scratch.
                    watchedPackage = null
                    watchedDecision = null
                }
        }
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
        val windowBehind = foregroundWindows?.isBehindAnotherWindow(windowId) == true

        val foreground = foregroundResolver.currentForegroundPackage()
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
                    "windowBehind=$windowBehind probe=$foreground refused=${windowBehind || probeTrailing})",
            )
        }
        return windowBehind || probeTrailing
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
     * Whether what the last re-check pass decided for [pkg] still holds, so the app does
     * not have to be decided again.
     *
     * A gate is settled only while its window is genuinely up, so a blocker the system
     * removed is raised again on the next pass. A grant is settled only while it is still
     * live: a grant that lapses or is revoked mid-use brings the blocker up without
     * waiting for the user to leave and come back. A passing app is never settled —
     * re-deciding is what lets a target enabled while its app is open start being
     * blocked, and it covers the enforcement cache still being cold on the first passes.
     */
    private fun stillSettled(pkg: String): Boolean = when (watchedDecision) {
        GateDecision.PASS -> false

        GateDecision.GRANTED ->
            repository.cachedGrant(pkg)?.endAtMillis?.let { it > System.currentTimeMillis() } == true

        GateDecision.GATE, GateDecision.REVOKE, GateDecision.LOCKOUT ->
            overlayHost.shownForPackage == pkg

        null -> false
    }

    fun onForeground(
        pkg: String,
        surface: ForegroundSurface,
        className: String? = null,
        windowId: Int = 0,
    ) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "FOREGROUND_CHANGED(pkg=$pkg surface=$surface class=$className window=$windowId)")
        }
        scope.launch {
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
            repository.revokeGrant(pkg, reason = "expired")
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
        val decision = GateDecider.decide(
            targetEnabled = target != null,
            grant = repository.cachedGrant(pkg)?.let {
                GrantSnapshot(it.packageName, it.endAtMillis, it.lastSeenMillis)
            },
            lockout = if (debtServed) {
                null
            } else {
                repository.cachedLockout(pkg)?.let {
                    LockoutSnapshot(it.packageName, it.untilMillis, 0L)
                }
            },
            nowMillis = now,
            debtAtCap = debtAtCap,
        )

        val label = target?.label?.takeIf { it.isNotBlank() }
            ?: pkg.substringAfterLast('.').uppercase()

        when (decision) {
            GateDecision.PASS -> overlayHost.dismiss(reason = "not a target: $pkg")

            GateDecision.GRANTED -> {
                repository.recordLastSeen(pkg, now)
                overlayHost.dismiss(reason = "granted: $pkg")
            }

            GateDecision.REVOKE -> {
                repository.revokeGrant(pkg, reason = "absence")
                overlayHost.showGate(pkg, label)
            }

            GateDecision.LOCKOUT -> overlayHost.showLockout(
                pkg = pkg,
                label = label,
                // A debt at the ceiling waits out its lockout; if that row is somehow
                // already gone, the debt itself is the time still owed.
                remainingMillis = maxOf(
                    repository.lockoutRemainingMillis(pkg, now),
                    if (debtAtCap) debtMillis else 0L,
                ),
            )

            GateDecision.GATE -> overlayHost.showGate(pkg, label)
        }

        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "pkg=$pkg surface=$surface class=$className decision=$decision " +
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
        foregroundWindows = null
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
    }
}
