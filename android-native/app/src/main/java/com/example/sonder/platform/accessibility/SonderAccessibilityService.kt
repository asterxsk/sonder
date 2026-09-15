package com.example.sonder.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import com.example.sonder.platform.enforcement.EnforcementCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground app detection (plan §3). Listens to window state changes only,
 * never retrieves window content. Events are debounced; our own package and
 * system UI packages are ignored.
 */
@AndroidEntryPoint
class SonderAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: EnforcementCoordinator

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = EVENT_DEBOUNCE_MILLIS
        }
        AccessibilityGate.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return // never react to ourselves
        if (pkg in IGNORED_PACKAGES) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Offload DB work off the main thread.
        coordinator.onWindowEvent(pkg, this)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        AccessibilityGate.onServiceDisconnected()
        super.onDestroy()
    }

    companion object {
        const val EVENT_DEBOUNCE_MILLIS = 800L

        /** System surfaces that must never trigger enforcement. */
        val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.android.systemui.navigationbar",
        )
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
