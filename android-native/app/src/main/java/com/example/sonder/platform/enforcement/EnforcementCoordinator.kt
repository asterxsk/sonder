package com.example.sonder.platform.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.platform.accessibility.SonderAccessibilityService
import com.example.sonder.platform.block.BlockActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Receives foreground window events from the accessibility service and decides:
 * 1. Is the user returning to a granted app after an absence? → revoke if >60s.
 * 2. Is the app a blocked target without an active grant? → instant overlay + gate.
 * 3. Is the target locked out? → lockout overlay.
 */
@Singleton
class EnforcementCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EnforcementRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Overlay bookkeeping is touched from two threads: the Default-dispatcher
    // coroutines that raise it (onWindowEvent / onGateHandoffFailed) and the main
    // thread that tears it down (onGateShown / onGateDismissed, and the overlay's
    // own auto-dismiss via its main-thread handler). @Volatile keeps a reader from
    // observing a stale cache; every compound update goes through a @Synchronized
    // method so a concurrent raise/teardown cannot interleave and orphan a view.
    @Volatile
    private var overlay: BlockOverlay? = null

    @Volatile
    private var overlayShownFor: String? = null

    /**
     * The package whose gate most recently finished. Used only to let a re-pin
     * request from a failed handoff detect that a newer dismissal superseded it;
     * it is deliberately NOT a time window. A window that swallowed a same-package
     * window event would also swallow a genuine fast reopen, which is exactly the
     * un-gated state the gate exists to prevent. The un-granted exit hands the user
     * to the launcher, so teardown cannot re-reveal the blocked app and no such
     * suppression is needed.
     */
    @Volatile
    private var lastDismissedPkg: String? = null

    /** Target package awaiting the blackjack gate (consumed by BlockActivity). */
    @Volatile
    var pendingTargetPackage: String? = null
        private set

    fun onWindowEvent(pkg: String, service: AccessibilityService) {
        scope.launch {
            val now = System.currentTimeMillis()

            // 1) User back inside a granted app: absence check first, then touch last-seen.
            if (repository.hasActiveGrant(pkg, now)) {
                val revoked = repository.evaluateAbsence(pkg, now)
                if (revoked) {
                    // Absence >60s: access revoked — fall through to blocking logic below.
                    repository.recordLastSeen(pkg, now)
                } else {
                    repository.recordLastSeen(pkg, now)
                    dismissOverlayIfFor(pkg)
                    return@launch
                }
            }

            // 2) Not a target (or disabled): make sure no stale overlay is up.
            if (!repository.isTargetEnabled(pkg)) {
                dismissOverlayIfFor(pkg)
                return@launch
            }

            // 3) Granted apps pass; everything else gets gated or locked out.
            if (repository.hasActiveGrant(pkg, now)) return@launch

            if (isOverlayShownFor(pkg)) return@launch // already gated this app

            val lockoutRemaining = repository.lockoutRemainingMillis(pkg, now)
            showOverlay(
                pkg = pkg,
                lockoutRemainingMillis = lockoutRemaining,
                lockoutReason = repository.lockoutReason(pkg),
                winGrantMillis = repository.rulesFor(pkg).winGrantMillis,
            )
            launchGate(pkg)
        }
    }

    /**
     * Called by BlockActivity whenever the gate finishes. Drops the overlay, and
     * records [pkg] only so a later failed-handoff re-pin can tell a newer dismissal
     * apart from its own.
     *
     * It deliberately does NOT re-pin. An un-granted exit hands the user to the
     * launcher, and launcher window events never reach here (the service drops
     * them), so the departure is unobservable from this side — any "is the app
     * still in front?" test here would read a stale value and re-trap the user on
     * the home screen. Re-pinning is requested explicitly by the only code that can
     * tell a real handoff from a failed one.
     */
    fun onGateDismissed(pkg: String?) {
        lastDismissedPkg = pkg
        dismissOverlay()
    }

    /**
     * The un-granted exit could not resolve a HOME activity, so the blocked app is
     * left in front with nothing over it. Once the teardown has settled, re-pin the
     * overlay and gate — the overlay must go up BEFORE the gate, since its
     * visibility is what keeps the launch BAL-exempt on API 29+.
     */
    fun onGateHandoffFailed(pkg: String?) {
        if (pkg == null) return
        scope.launch {
            delay(HANDOFF_RETRY_MILLIS)
            val now = System.currentTimeMillis()
            if (lastDismissedPkg != pkg) return@launch        // a newer dismissal superseded this one
            if (!repository.isTargetEnabled(pkg)) return@launch
            if (repository.hasActiveGrant(pkg, now)) return@launch // user won — access stands
            showOverlay(
                pkg = pkg,
                lockoutRemainingMillis = repository.lockoutRemainingMillis(pkg, now),
                lockoutReason = repository.lockoutReason(pkg),
                winGrantMillis = repository.rulesFor(pkg).winGrantMillis,
            )
            launchGate(pkg)
        }
    }

    fun launchGate(pkg: String) {
        pendingTargetPackage = pkg
        val intent = Intent(context, BlockActivity::class.java).apply {
            // NEW_TASK: we start from a service context.
            // CLEAR_TOP|SINGLE_TOP: reuse/replace any existing gate on top.
            // NEVER CLEAR_TASK — it would destroy MainActivity, leaving Sonder's
            // task empty so any later back/finish dumps the user on the launcher
            // (the "app disappeared / can't go home" bug).
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
            putExtra(BlockActivity.EXTRA_TARGET_PACKAGE, pkg)
        }
        context.startActivity(intent)
    }

    @Synchronized
    private fun showOverlay(
        pkg: String,
        lockoutRemainingMillis: Long,
        lockoutReason: String?,
        winGrantMillis: Long,
    ) {
        dismissOverlay()
        val o = BlockOverlay(
            context = context,
            targetLabel = pkg,
            lockoutRemainingMillis = lockoutRemainingMillis,
            winGrantMillis = winGrantMillis,
            lockoutReason = lockoutReason,
            // Overlay tap = open the gate activity (BAL-exempt: overlay is visible).
            onTap = { launchGate(pkg) },
            // Self-removal must clear our bookkeeping, or overlayShownFor stays set
            // and the guard above swallows every later window event for this package.
            onAutoDismiss = { dismissOverlayIfFor(pkg) },
        )
        if (o.show()) {
            overlay = o
            overlayShownFor = pkg
        }
    }

    /** Called by BlockActivity once it is in front — overlay has done its instant-block job. */
    fun onGateShown() {
        dismissOverlay()
    }

    @Synchronized
    fun dismissOverlayIfFor(pkg: String) {
        if (overlayShownFor == pkg) dismissOverlay()
    }

    @Synchronized
    private fun isOverlayShownFor(pkg: String): Boolean = overlayShownFor == pkg

    @Synchronized
    fun dismissOverlay() {
        overlay?.dismiss()
        overlay = null
        overlayShownFor = null
    }

    companion object {
        /**
         * Delay before a failed handoff re-pins. Long enough for the gate teardown
         * to settle so the re-pin does not race the departing activity.
         */
        const val HANDOFF_RETRY_MILLIS = 1_500L
    }
}
