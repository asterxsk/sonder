package com.example.sonder.platform.permissions

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.example.sonder.platform.accessibility.SonderAccessibilityService
import com.example.sonder.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class SonderPermission(val label: String) {
    ACCESSIBILITY("Accessibility"),
    OVERLAY("Display over other apps"),
    USAGE_ACCESS("Usage access"),
    NOTIFICATIONS("Notifications"),
}

/**
 * Permission state checks (plan §4). Every check reads the system directly and caches
 * nothing, so a caller that re-audits on resume always sees the truth.
 *
 * The 10-second on-open audit that used to live here now sits in [com.example.sonder.MainActivity],
 * where the onboarding flag is available — accessibility state is flaky right at app
 * start-up, so it still waits [AUDIT_DELAY_MILLIS] before concluding anything is missing.
 */
@Singleton
class PermissionAudit @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun missingPermissions(): Set<SonderPermission> =
        SonderPermission.entries.filterNot(::isGranted).toSet()

    /**
     * Live state for one permission. [PermissionHandoff] polls this while the user is
     * away in Settings, so it reads the system on every call and caches nothing.
     */
    fun isGranted(permission: SonderPermission): Boolean = when (permission) {
        SonderPermission.ACCESSIBILITY -> isAccessibilityEnabled()
        SonderPermission.OVERLAY -> Settings.canDrawOverlays(context)
        SonderPermission.USAGE_ACCESS -> isUsageAccessGranted()
        SonderPermission.NOTIFICATIONS -> isNotificationPermissionGranted()
    }

    fun isAccessibilityEnabled(): Boolean {
        if (com.example.sonder.platform.accessibility.AccessibilityGate.isLive()) return true
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        val expected = ComponentName(context, SonderAccessibilityService::class.java)
        return enabled.any { it.resolveInfo?.serviceInfo?.let { si ->
            ComponentName(si.packageName, si.name)
        } == expected }
    }

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isNotificationPermissionGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // pre-33: notification permission does not exist
        }

    companion object {
        /** Grace period after open, so accessibility state has settled before we judge. */
        const val AUDIT_DELAY_MILLIS = 10_000L
    }
}
