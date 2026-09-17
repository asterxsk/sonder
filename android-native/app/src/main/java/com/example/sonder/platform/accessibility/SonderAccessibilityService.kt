package com.example.sonder.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.sonder.domain.ForegroundSurface
import com.example.sonder.platform.enforcement.EnforcementCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground detection for the blocker (plan §3). Window-state events only,
 * never window content.
 *
 * Every event is classified into a [ForegroundSurface]: transient system
 * surfaces are ignored, home/recents and the detox app release the blocker, and
 * real apps are gated when they are blocked targets. Launchers and the blocker
 * itself therefore no longer need to be filtered out *by package name* to avoid
 * false gating — instead they are handled by the decision layer, which is what
 * lets the blocker reliably disappear when the user goes Home.
 *
 * [notificationTimeout] stays tiny: it is the delay Android may add before
 * delivering an event, and the blocker must land while the app is still coming
 * up.
 */
@AndroidEntryPoint
class SonderAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: EnforcementCoordinator

    private var receiverRegistered = false

    /** Screen off = the blocker must go: it must never cover the keyguard. */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) coordinator.onScreenOff()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = EVENT_DEBOUNCE_MILLIS
        }
        registerScreenOffReceiver()
        AccessibilityGate.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The class name separates our own Activities (the detox app opening,
        // which must release the blocker) from our own overlay window (the
        // blocker itself, whose event must never dismiss it).
        val className = event.className?.toString()
        coordinator.onForeground(pkg, ForegroundSurface.classify(pkg, packageName, className), className)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // The service is going away: take the blocker down with it.
        coordinator.onServiceStopped()
        unregisterScreenOffReceiver()
        AccessibilityGate.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        coordinator.onServiceStopped()
        unregisterScreenOffReceiver()
        AccessibilityGate.onServiceDisconnected()
        super.onDestroy()
    }

    private fun registerScreenOffReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenOffReceiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!receiverRegistered) return
        receiverRegistered = false
        runCatching { unregisterReceiver(screenOffReceiver) }
    }

    companion object {
        /** Max delay Android may add before delivering a window event. */
        const val EVENT_DEBOUNCE_MILLIS = 50L
    }
}

/** Static bridge so the UI can check whether the service is live (audit + onboarding). */
object AccessibilityGate {
    @Volatile
    private var connected: Boolean = false

    fun onServiceConnected(service: AccessibilityService) {
        connected = true
    }

    fun onServiceDisconnected() {
        connected = false
    }

    fun isLive(): Boolean = connected
}
