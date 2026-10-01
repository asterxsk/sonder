package com.example.sonder.platform.permissions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import com.example.sonder.MainActivity
import com.example.sonder.platform.accessibility.SonderAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The one place that knows how to send the user to a system permission screen, and the
 * one place that knows how to get them back.
 *
 * Android has no "tell me when they granted it" callback for overlays, usage access or
 * notifications, so the only way to return automatically is to watch the grant from
 * behind the Settings screen. That watcher is why this is a singleton object rather than
 * a per-call helper: the caller must be able to stop it, or a dialog the user dismissed
 * would pull the app forward minutes later.
 *
 * Both the waiting poll and the return launch are best-effort. If the system declines
 * either (a frozen process, a background-activity-start block) the user simply comes back
 * by hand, and every caller re-audits on resume — so a missed return is a worse feel,
 * never a wrong state.
 */
object PermissionHandoff {

    /** Long enough not to spin a binder call, short enough to feel instant on a toggle. */
    private const val POLL_MILLIS = 350L

    /** Gives up after this long so a dismissed request cannot linger indefinitely. */
    private const val WATCH_MILLIS = 120_000L

    private var watch: Job? = null

    /** The system screen for [permission]. Deep-links where the platform allows one. */
    fun settingsIntent(context: Context, permission: SonderPermission): Intent =
        when (permission) {
            SonderPermission.ACCESSIBILITY ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    // Named by class, not by string: R8 rewrites the class and the
                    // manifest together, but a string literal holding the old name
                    // would survive and point at nothing in a release build.
                    putExtra(
                        Intent.EXTRA_COMPONENT_NAME,
                        ComponentName(context, SonderAccessibilityService::class.java),
                    )
                }
            SonderPermission.OVERLAY ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                )
            SonderPermission.USAGE_ACCESS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SonderPermission.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
        }

    /**
     * Opens the system screen for [permission], then watches for up to two minutes and
     * brings Sonder back the moment the grant lands.
     *
     * [beforeReturn] runs first, while the app is still in the background — the prompt
     * dialog uses it to re-audit, so it is already listing whatever is still outstanding
     * by the time the user is brought forward, and is gone if that is nothing.
     *
     * Only the requested permission is watched, so a user who grants it and then keeps
     * changing other settings is not interrupted until that one lands.
     */
    fun request(
        context: Context,
        permission: SonderPermission,
        beforeReturn: () -> Unit = {},
    ) {
        val app = context.applicationContext
        cancel()

        runCatching {
            app.startActivity(
                settingsIntent(app, permission).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        // Stateless and cheap to build; the check reads live system state per call.
        val audit = PermissionAudit(app)
        watch = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            val deadline = SystemClock.elapsedRealtime() + WATCH_MILLIS
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(POLL_MILLIS)
                if (!audit.isGranted(permission)) continue
                watch = null // finished; nothing left for cancel() to do
                beforeReturn()
                bringToFront(app)
                return@launch
            }
        }
    }

    /** Stops watching. Safe to call when nothing is in flight. */
    fun cancel() {
        watch?.cancel()
        watch = null
    }

    /**
     * Raises the existing task. MainActivity is the task root, so NEW_TASK alone finds
     * that task and pulls it forward with its back stack intact rather than stacking a
     * second copy of the app.
     */
    private fun bringToFront(app: Context) {
        runCatching {
            app.startActivity(
                Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
