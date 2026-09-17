package com.example.sonder.platform.enforcement

import android.content.Context
import android.util.Log
import com.example.sonder.BuildConfig
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.domain.ForegroundSurface
import com.example.sonder.domain.GateDecision
import com.example.sonder.domain.GateDecider
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot
import com.example.sonder.platform.foreground.ForegroundResolver
import com.example.sonder.platform.overlay.GateOverlayHost
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 * reaches the foreground. Because the event stream can arrive slightly out of
 * order during launch transitions, releasing the blocker is verified against the
 * real foreground package and re-applied if the app is in fact still blocked.
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

    /** Pending post-release re-check; superseded by every fresh app event. */
    private var verifyJob: Job? = null

    fun onForeground(pkg: String, surface: ForegroundSurface, className: String? = null) {
        scope.launch {
            when (surface) {
                // Our own overlay window reports our package with a non-app class
                // name; ignoring it is what stops the blocker from dismissing itself.
                ForegroundSurface.TRANSIENT -> return@launch

                ForegroundSurface.HOME, ForegroundSurface.OWN -> {
                    overlayHost.dismiss(reason = "surface=$surface pkg=$pkg")
                    // The launcher's event can trail the app's own event during a
                    // launch, so confirm what is really on screen shortly after.
                    scheduleForegroundVerification()
                    return@launch
                }

                ForegroundSurface.APP -> Unit
            }

            evaluate(pkg = pkg, surface = surface, className = className, cancelVerification = true)
        }
    }

    /**
     * The single decision path, shared by live events and the after-release
     * verification pass.
     */
    private suspend fun evaluate(
        pkg: String,
        surface: ForegroundSurface,
        className: String?,
        cancelVerification: Boolean,
    ) {
        if (cancelVerification) {
            verifyJob?.cancel()
            verifyJob = null
        }

        val now = System.currentTimeMillis()

        // Housekeeping: purge an expired grant row so enforcement resumes.
        repository.cachedGrant(pkg)?.takeIf { it.endAtMillis <= now }?.let {
            repository.revokeGrant(pkg, reason = "expired")
        }

        val target = repository.enabledTarget(pkg)
        val decision = GateDecider.decide(
            targetEnabled = target != null,
            grant = repository.cachedGrant(pkg)?.let {
                GrantSnapshot(it.packageName, it.endAtMillis, it.lastSeenMillis)
            },
            lockout = repository.cachedLockout(pkg)?.let {
                LockoutSnapshot(it.packageName, it.untilMillis, 0L)
            },
            nowMillis = now,
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
                remainingMillis = repository.lockoutRemainingMillis(pkg, now),
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
    }

    /**
     * Ask the OS what is really in the foreground a moment after releasing the
     * blocker. If a blocked target is still on screen (because the release came
     * from a trailing launcher/self event), the blocker comes back.
     */
    private fun scheduleForegroundVerification() {
        verifyJob?.cancel()
        verifyJob = scope.launch {
            delay(VERIFY_DELAY_MILLIS)
            val foreground = foregroundResolver.currentForegroundPackage() ?: return@launch
            if (foreground == ownPackage) return@launch // our own UI: nothing to block

            if (BuildConfig.DEBUG) Log.d(TAG, "verification: foreground=$foreground")
            evaluate(
                pkg = foreground,
                surface = ForegroundSurface.classify(foreground, ownPackage),
                className = null,
                cancelVerification = false,
            )
        }
    }

    /** Screen off: never leave the blocker covering the keyguard. */
    fun onScreenOff() {
        verifyJob?.cancel()
        verifyJob = null
        overlayHost.dismiss(reason = "screen off")
    }

    /** Service torn down / unbound: the blocker must not outlive it. */
    fun onServiceStopped() {
        verifyJob?.cancel()
        verifyJob = null
        overlayHost.dismiss(reason = "service stopped")
    }

    private companion object {
        const val TAG = "SonderGate"

        /** Long enough for the OS to settle on the real foreground, short enough to feel instant. */
        const val VERIFY_DELAY_MILLIS = 400L
    }
}
