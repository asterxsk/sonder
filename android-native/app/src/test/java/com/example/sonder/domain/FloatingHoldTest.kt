package com.example.sonder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that stops the blocker strobing over a floating video: one pass with no float is
 * not evidence the float ended.
 */
class FloatingHoldTest {

    private val yt = "com.google.android.youtube"
    private val insta = "com.instagram.android"

    private fun pass(hold: FloatingHold.Hold, foundNow: String?) =
        FloatingHold.afterPass(hold, foundNow = foundNow)

    @Test
    fun `a float found now starts the hold`() {
        val hold = pass(FloatingHold.NONE, yt)

        assertEquals(yt, hold.coveredFor)
        assertEquals(0, hold.misses)
        assertTrue(hold.active)
    }

    @Test
    fun `one empty pass keeps the hold`() {
        val hold = pass(FloatingHold.Hold(coveredFor = yt), null)

        assertEquals(yt, hold.coveredFor)
        assertEquals(1, hold.misses)
    }

    @Test
    fun `the hold ends only on the pass that reaches the confirming count`() {
        var hold = FloatingHold.Hold(coveredFor = yt)
        repeat(FloatingHold.CONFIRMING_PASSES - 1) {
            hold = pass(hold, null)
            assertEquals("hold released after ${it + 1} empty passes", yt, hold.coveredFor)
        }

        val last = pass(hold, null)

        assertEquals(null, last.coveredFor)
        assertEquals(0, last.misses)
        assertFalse(last.active)
    }

    @Test
    fun `a float seen mid-count resets the run rather than releasing`() {
        // The flash this rule exists for: a pass that finds the float again after the window
        // list briefly lost it must restore the hold, not let it age out.
        val hold = pass(FloatingHold.Hold(coveredFor = yt, misses = 2), yt)

        assertEquals(yt, hold.coveredFor)
        assertEquals(0, hold.misses)
    }

    @Test
    fun `a different app floating takes the hold over`() {
        val hold = pass(FloatingHold.Hold(coveredFor = yt, misses = 1), insta)

        assertEquals(insta, hold.coveredFor)
        assertEquals(0, hold.misses)
    }

    @Test
    fun `no hold and no float stays released`() {
        val hold = pass(FloatingHold.NONE, null)

        assertEquals(FloatingHold.NONE, hold)
        assertFalse(hold.active)
    }

    @Test
    fun `stale misses never carry into a fresh hold`() {
        // A hold that has been dropped must not release the next float early: the counter it
        // leaves behind is zero, not the run that ended the previous hold.
        val ended = pass(FloatingHold.Hold(coveredFor = yt, misses = FloatingHold.CONFIRMING_PASSES - 1), null)
        assertEquals(0, ended.misses)

        val next = pass(ended, yt)
        assertEquals(0, next.misses)
    }

    @Test
    fun `nothing floating and no hold allows a release`() {
        assertTrue(FloatingHold.releaseAllowed(pkg = null, floating = null, hold = FloatingHold.NONE))
    }

    @Test
    fun `a float on screen refuses a release that speaks for whatever is in front`() {
        // The strobe, stated as a rule: the re-check pass resolves the launcher and would
        // call it Home, and the blocker comes off the app still playing in the float.
        assertFalse(FloatingHold.releaseAllowed(pkg = null, floating = yt, hold = FloatingHold.NONE))
    }

    @Test
    fun `a hold refuses a release even after the float has been missed`() {
        // The pass that missed the float is the one that used to dismiss.
        assertFalse(FloatingHold.releaseAllowed(pkg = null, floating = null, hold = FloatingHold.Hold(yt)))
        assertFalse(FloatingHold.releaseAllowed(pkg = null, floating = yt, hold = FloatingHold.Hold(yt)))
    }

    @Test
    fun `an app may be released when it is the one floating`() {
        // Granting the floating app its own time is the point of taking the blocker off it.
        assertTrue(FloatingHold.releaseAllowed(pkg = yt, floating = yt, hold = FloatingHold.Hold(yt)))
        assertTrue(FloatingHold.releaseAllowed(pkg = yt, floating = yt, hold = FloatingHold.NONE))
    }

    @Test
    fun `a grant for another app never uncovers a float`() {
        assertFalse(FloatingHold.releaseAllowed(pkg = insta, floating = yt, hold = FloatingHold.NONE))
        assertFalse(FloatingHold.releaseAllowed(pkg = insta, floating = null, hold = FloatingHold.Hold(yt)))
        assertFalse(FloatingHold.releaseAllowed(pkg = insta, floating = yt, hold = FloatingHold.Hold(yt)))
    }

    @Test
    fun `a grant for another app is allowed once the float is gone and the hold has expired`() {
        assertTrue(FloatingHold.releaseAllowed(pkg = insta, floating = null, hold = FloatingHold.NONE))
    }
}
