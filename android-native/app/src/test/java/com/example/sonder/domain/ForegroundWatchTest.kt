package com.example.sonder.domain

import com.example.sonder.domain.ForegroundSurface.APP
import com.example.sonder.domain.ForegroundSurface.HOME
import com.example.sonder.domain.ForegroundSurface.OWN
import com.example.sonder.domain.ForegroundSurface.TRANSIENT
import com.example.sonder.domain.ForegroundWatch.Action
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The re-check policy for the foreground the event stream did not report —
 * the reason a blocked app no longer stays visible for seconds (or for good)
 * behind an app-lock window or on the way back from Recents.
 */
class ForegroundWatchTest {

    private fun actionFor(surface: ForegroundSurface, settled: Boolean, awayPasses: Int) =
        ForegroundWatch.actionFor(surface = surface, settled = settled, awayPasses = awayPasses)

    @Test
    fun `an app on screen is always decided`() {
        assertEquals(Action.DECIDE, actionFor(APP, settled = false, awayPasses = 0).action)
    }

    @Test
    fun `an app whose decision still holds is left alone`() {
        // The gate is up for it, or its grant is still live: nothing to re-apply.
        assertEquals(Action.IGNORE, actionFor(APP, settled = true, awayPasses = 0).action)
    }

    @Test
    fun `a decided app never carries an away-pass forward`() {
        assertEquals(0, actionFor(APP, settled = true, awayPasses = 5).awayPasses)
        assertEquals(0, actionFor(APP, settled = false, awayPasses = 5).awayPasses)
    }

    @Test
    fun `home releases only once the next pass agrees`() {
        // A foreground lookup can still read "launcher" a moment after the app came up,
        // and releasing on that one reading would uncover the app.
        val first = actionFor(HOME, settled = false, awayPasses = 0)
        assertEquals(Action.IGNORE, first.action)
        assertEquals(1, first.awayPasses)

        assertEquals(Action.RELEASE, actionFor(HOME, settled = false, awayPasses = first.awayPasses).action)
    }

    @Test
    fun `the detox app releasing follows the same confirmation`() {
        val first = actionFor(OWN, settled = false, awayPasses = 0)
        assertEquals(Action.IGNORE, first.action)
        assertEquals(Action.RELEASE, actionFor(OWN, settled = false, awayPasses = first.awayPasses).action)
    }

    @Test
    fun `an app breaking the run cancels the pending release`() {
        // Launcher, then the app arriving late: the release must not fire on the tail of it.
        val home = actionFor(HOME, settled = false, awayPasses = 0)
        val app = actionFor(APP, settled = false, awayPasses = home.awayPasses)
        assertEquals(0, app.awayPasses)
        assertEquals(Action.IGNORE, actionFor(HOME, settled = false, awayPasses = app.awayPasses).action)
    }

    @Test
    fun `transient surfaces say nothing and never count as a release`() {
        val seen = actionFor(TRANSIENT, settled = false, awayPasses = 5)
        assertEquals(Action.IGNORE, seen.action)
        assertEquals(0, seen.awayPasses)
    }

    @Test
    fun `a released run keeps releasing while the user stays away`() {
        // Already-confirmed: the count keeps its footing instead of asking for two more passes.
        assertEquals(Action.RELEASE, actionFor(HOME, settled = false, awayPasses = 2).action)
    }

    // --- release ordering (the Recents flicker) -------------------------------------

    private val blocked = "com.blocked.app"
    private val own = "com.example.sonder"

    private fun trailing(
        eventPkg: String = "com.android.launcher3",
        foreground: String? = blocked,
        foregroundBlocked: Boolean = true,
    ) = ForegroundWatch.isTrailingRelease(
        eventPkg = eventPkg,
        foreground = foreground,
        ownPackage = own,
        foregroundBlocked = foregroundBlocked,
    )

    @Test
    fun `a launcher event is refused while a blocked app is still the real foreground`() {
        // The Recents flicker: the launcher's event lands after the blocked app's, and
        // releasing on it would uncover an app the user is looking at.
        assertEquals(true, trailing())
    }

    @Test
    fun `a release is honoured once the launcher really is the foreground`() {
        assertEquals(false, trailing(foreground = "com.android.launcher3"))
    }

    @Test
    fun `the detox app opening releases even when the lookup lags`() {
        // Our own UI has to be reachable: its activities resume like any other.
        assertEquals(false, trailing(eventPkg = own, foreground = own))
    }

    @Test
    fun `an unknown foreground releases`() {
        // No usage access means no second opinion, so the event stands as before.
        assertEquals(false, trailing(foreground = null))
    }

    @Test
    fun `a non-target app foreground releases`() {
        assertEquals(false, trailing(foreground = "com.instagram.android", foregroundBlocked = false))
    }

    @Test
    fun `an event naming the still-foreground package releases`() {
        // Nothing contradicts it: the window that reported the release is the one in front.
        assertEquals(false, trailing(eventPkg = blocked, foreground = blocked))
    }
}
