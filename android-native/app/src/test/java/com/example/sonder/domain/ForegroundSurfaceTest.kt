package com.example.sonder.domain

import com.example.sonder.domain.ForegroundSurface.APP
import com.example.sonder.domain.ForegroundSurface.HOME
import com.example.sonder.domain.ForegroundSurface.OWN
import com.example.sonder.domain.ForegroundSurface.TRANSIENT
import org.junit.Assert.assertEquals
import org.junit.Test

/** The classifier that decides whether a window event may be gated at all. */
class ForegroundSurfaceTest {

    private val own = "com.example.sonder"

    private fun classify(pkg: String, className: String? = null) =
        ForegroundSurface.classify(pkg, own, className)

    @Test
    fun `the detox app is OWN when one of its Activities opens`() {
        assertEquals(OWN, classify(own, "$own.MainActivity"))
        assertEquals(OWN, classify(own, "$own.platform.permissions.PermissionPromptActivity"))
    }

    @Test
    fun `our own overlay window is never mistaken for the detox app`() {
        // The blocker is our package's window; its own event must not dismiss it.
        assertEquals(TRANSIENT, classify(own, "androidx.compose.ui.platform.ComposeView"))
        assertEquals(TRANSIENT, classify(own, null))
    }

    @Test
    fun `real apps are APP`() {
        assertEquals(APP, classify("com.whatsapp"))
        assertEquals(APP, classify("com.instagram.android"))
        assertEquals(APP, classify("com.google.android.youtube"))
        // Settings is a normal app: it may be blocked if the user chooses it.
        assertEquals(APP, classify("com.android.settings"))
    }

    @Test
    fun `system surfaces are TRANSIENT`() {
        assertEquals(TRANSIENT, classify("com.android.systemui"))
        assertEquals(TRANSIENT, classify("com.android.keyguard"))
        assertEquals(TRANSIENT, classify("com.android.permissioncontroller"))
        assertEquals(TRANSIENT, classify("android"))
    }

    @Test
    fun `keyboards are TRANSIENT`() {
        assertEquals(TRANSIENT, classify("com.google.android.inputmethod.latin"))
        assertEquals(TRANSIENT, classify("com.android.inputmethod.latin"))
        assertEquals(TRANSIENT, classify("com.samsung.android.honeyboard"))
        assertEquals(TRANSIENT, classify("com.example.custom.ime"))
    }

    @Test
    fun `launchers and recents are HOME`() {
        assertEquals(HOME, classify("com.oneplus.launcher"))
        assertEquals(HOME, classify("net.oneplus.launcher"))
        assertEquals(HOME, classify("com.android.launcher3"))
        assertEquals(HOME, classify("com.google.android.apps.nexuslauncher"))
        assertEquals(HOME, classify("com.sec.android.app.launcher"))
        assertEquals(HOME, classify("com.miui.home"))
        assertEquals(HOME, classify("com.android.quickstep"))
    }

    @Test
    fun `packages merely containing ime letters are not misread as keyboards`() {
        // "ime" appears inside these package names but they are ordinary apps.
        assertEquals(APP, classify("com.example.time"))
        assertEquals(APP, classify("com.timemaster.tracker"))
        assertEquals(APP, classify("com.crimewatch.app"))
    }

    @Test
    fun `the quick search box is HOME not an app`() {
        assertEquals(HOME, classify("com.google.android.googlequicksearchbox"))
    }
}
