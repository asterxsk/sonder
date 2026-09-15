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
    private var overlay: BlockOverlay? = null
    private var overlayShownFor: String? = null

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

            if (overlayShownFor == pkg) return@launch // already gated this app

            val lockoutRemaining = repository.lockoutRemainingMillis(pkg, now)
            showOverlay(pkg, lockoutRemaining)
            launchGate(pkg)
        }
    }

    fun launchGate(pkg: String) {
        pendingTargetPackage = pkg
        val intent = Intent(context, BlockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(BlockActivity.EXTRA_TARGET_PACKAGE, pkg)
        }
        context.startActivity(intent)
    }

    private fun showOverlay(pkg: String, lockoutRemainingMillis: Long) {
        dismissOverlay()
        val o = BlockOverlay(context, pkg, lockoutRemainingMillis) {
            // Overlay tap = open the gate activity (BAL-exempt: overlay is visible).
            launchGate(pkg)
        }
        if (o.show()) {
            overlay = o
            overlayShownFor = pkg
        }
    }

    /** Called by BlockActivity once it is in front — overlay has done its instant-block job. */
    fun onGateShown() {
        dismissOverlay()
    }

    fun dismissOverlayIfFor(pkg: String) {
        if (overlayShownFor == pkg) dismissOverlay()
    }

    @Synchronized
    fun dismissOverlay() {
        overlay?.dismiss()
        overlay = null
        overlayShownFor = null
    }
}
