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
 * Permission state checks + the delayed audit (plan §4):
 * accessibility state can be flaky right at app start-up, so the on-open audit
 * deliberately waits 10 seconds before concluding a permission is missing —
 * exactly the behavior the user asked for.
 */
@Singleton
class PermissionAudit @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun missingPermissions(): Set<SonderPermission> {
        val missing = mutableSetOf<SonderPermission>()
        if (!isAccessibilityEnabled()) missing += SonderPermission.ACCESSIBILITY
        if (!Settings.canDrawOverlays(context)) missing += SonderPermission.OVERLAY
        if (!isUsageAccessGranted()) missing += SonderPermission.USAGE_ACCESS
        if (!isNotificationPermissionGranted()) missing += SonderPermission.NOTIFICATIONS
        return missing
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
        const val AUDIT_DELAY_MILLIS = 10_000L

        /**
         * The on-open audit: 10 seconds after Sonder opens, if something is off,
         * surface the pixel prompt. Marker-based: once granted, never prompts again.
         */
        fun scheduleOnOpenAudit(context: Context) {
            android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(
                    {
                        try {
                            val audit = PermissionAudit(context.applicationContext)
                            if (audit.missingPermissions().isNotEmpty()) {
                                val i = android.content.Intent(context, PermissionPromptActivity::class.java)
                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(i)
                            }
                        } catch (_: Exception) {
                            // Never crash over the audit.
                        }
                    },
                    AUDIT_DELAY_MILLIS,
                )
        }
    }
}
