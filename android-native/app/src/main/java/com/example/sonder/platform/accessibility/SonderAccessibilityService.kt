package com.example.sonder.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
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

    /**
     * Screen off = the blocker must go: it must never cover the keyguard, and there is
     * nothing on screen to enforce against. Screen on starts that enforcement again.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> coordinator.onScreenOff()
                Intent.ACTION_SCREEN_ON -> coordinator.onScreenOn()
            }
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
        // The event stream is the fast path; this re-checks the real foreground for the
        // events it never delivers (see EnforcementCoordinator.onServiceConnected).
        coordinator.onServiceConnected(ForegroundWindows(::isBehindAnotherWindow))
        AccessibilityGate.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The class name separates our own Activities (the detox app opening,
        // which must release the blocker) from our own overlay window (the
        // blocker itself, whose event must never dismiss it).
        val className = event.className?.toString()
        // The service reports what it saw; the coordinator classifies it. One input the
        // classification needs — whether this package is an enabled target — lives on the
        // enforcement cache, and it is what stops the launcher/IME hints from exempting a
        // target the user chose to gate.
        coordinator.onForeground(
            pkg = pkg,
            className = className,
            // Carried along so a release can be checked against the window that reported
            // it: see ForegroundWindows.
            windowId = event.windowId,
        )
    }

    /**
     * The window list, top-most first, answering "is the window that reported a release
     * still the one in front" from metadata alone — no window content is read.
     */
    private fun isBehindAnotherWindow(windowId: Int): Boolean {
        val listed = try {
            windows
        } catch (_: Throwable) {
            // The service can be disconnected mid-call; no answer is better than a guess.
            return false
        } ?: return false

        // Our own overlay is deliberately not focusable, so while it covers a blocked app
        // the app's window keeps input focus: this answers "is that app still in front"
        // even with the blocker in the way.
        val focused = listed.firstOrNull { it.isActive } ?: return false

        // Only another *application* having focus is evidence. A system window or a
        // keyboard holding focus while a launcher event lands says nothing about where
        // the user is, and holding the blocker up on that would strand it over Home.
        if (focused.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return false
        if (focused.type == AccessibilityWindowInfo.TYPE_SYSTEM) return false

        return focused.id != windowId && listed.any { it.id == windowId }
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
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!receiverRegistered) return
        receiverRegistered = false
        runCatching { unregisterReceiver(screenReceiver) }
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
