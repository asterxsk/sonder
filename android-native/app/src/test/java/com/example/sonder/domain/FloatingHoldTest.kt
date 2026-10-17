package com.example.sonder.domain

import com.example.sonder.domain.FloatingHold.Answer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that stops the blocker strobing over a floating video: one pass with no float is
 * not evidence the float ended — and a pass that could not look is not evidence of anything.
 */
class FloatingHoldTest {

    private val yt = "com.google.android.youtube"
    private val insta = "com.instagram.android"

    private fun found(pkg: String) = Answer.Found(pkg)

    private fun pass(hold: FloatingHold.Hold, answer: Answer) =
        FloatingHold.afterPass(hold, answer)

    @Test
    fun `a float found now starts the hold`() {
        val hold = pass(FloatingHold.NONE, found(yt))

        assertEquals(yt, hold.coveredFor)
        assertEquals(0, hold.misses)
        assertTrue(hold.active)
    }

    @Test
    fun `one empty pass keeps the hold`() {
        val hold = pass(FloatingHold.Hold(coveredFor = yt), Answer.None)

        assertEquals(yt, hold.coveredFor)
        assertEquals(1, hold.misses)
    }

    @Test
    fun `the hold ends only on the pass that reaches the confirming count`() {
        var hold = FloatingHold.Hold(coveredFor = yt)
        repeat(FloatingHold.CONFIRMING_PASSES - 1) {
            hold = pass(hold, Answer.None)
            assertEquals("hold released after ${it + 1} empty passes", yt, hold.coveredFor)
        }

        val last = pass(hold, Answer.None)

        assertEquals(null, last.coveredFor)
        assertEquals(0, last.misses)
        assertFalse(last.active)
    }

    @Test
    fun `a float seen mid-count resets the run rather than releasing`() {
        // The flash this rule exists for: a pass that finds the float again after the window
        // list briefly lost it must restore the hold, not let it age out.
        val hold = pass(FloatingHold.Hold(coveredFor = yt, misses = 2), found(yt))

        assertEquals(yt, hold.coveredFor)
        assertEquals(0, hold.misses)
    }

    @Test
    fun `a different app floating takes the hold over`() {
        val hold = pass(FloatingHold.Hold(coveredFor = yt, misses = 1), found(insta))

        assertEquals(insta, hold.coveredFor)
        assertEquals(0, hold.misses)
    }

    @Test
    fun `no hold and no float stays released`() {
        val hold = pass(FloatingHold.NONE, Answer.None)

        assertEquals(FloatingHold.NONE, hold)
        assertFalse(hold.active)
    }

    @Test
    fun `stale misses never carry into a fresh hold`() {
        // A hold that has been dropped must not release the next float early: the counter it
        // leaves behind is zero, not the run that ended the previous hold.
        val ended = pass(FloatingHold.Hold(coveredFor = yt, misses = FloatingHold.CONFIRMING_PASSES - 1), Answer.None)
        assertEquals(0, ended.misses)

        val next = pass(ended, found(yt))
        assertEquals(0, next.misses)
    }

    // The bug this rewrite exists for. The probe is starved by the same busy service thread a
    // playing Short creates, so the passes that matter most are the ones that come back blind,
    // and every one of them used to be counted as "the float is gone".

    @Test
    fun `an unreadable pass does not advance the release count`() {
        val hold = pass(FloatingHold.Hold(coveredFor = yt, misses = 1), Answer.Unknown)

        assertEquals(yt, hold.coveredFor)
        assertEquals("a blind pass is not an empty one", 1, hold.misses)
    }

    @Test
    fun `unreadable passes alone can never release a hold`() {
        var hold = FloatingHold.Hold(coveredFor = yt)

        repeat(FloatingHold.CONFIRMING_PASSES * 4) {
            hold = pass(hold, Answer.Unknown)
        }

        assertEquals(yt, hold.coveredFor)
        assertEquals(0, hold.misses)
        assertTrue(hold.active)
    }

    @Test
    fun `an unreadable pass never starts a hold`() {
        // Unknown is not evidence of a float either: with nothing established there is nothing
        // for the blocker to be over, and inventing one would cover an app nobody named.
        val hold = pass(FloatingHold.NONE, Answer.Unknown)

        assertFalse(hold.active)
        assertEquals(FloatingHold.NONE, hold)
    }

    @Test
    fun `an unreadable pass does not abandon a hold mid count`() {
        // The exact sequence from the device: found, then the thread goes busy and the probe
        // comes back blind for as long as the Short keeps playing. The hold has to survive it.
        var hold = FloatingHold.Hold(coveredFor = yt, misses = FloatingHold.CONFIRMING_PASSES - 1)
        repeat(10) { hold = pass(hold, Answer.Unknown) }
        assertEquals(yt, hold.coveredFor)

        // And a single readable "no" then ends it, exactly as the rule always said.
        hold = pass(hold, Answer.None)
        assertFalse(hold.active)
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
    fun `an active hold refuses a front-speaking release with no float named`() {
        // The device's strobe, exactly: the float probe missed (or could not read) the float
        // while the hold was still standing, the re-check resolved the launcher, and the
        // "nothing is floating" branch took the blocker off the video being watched.
        assertFalse(FloatingHold.releaseAllowed(pkg = null, floating = null, hold = FloatingHold.Hold(coveredFor = yt)))
        assertFalse(FloatingHold.releaseAllowed(pkg = null, floating = null, hold = FloatingHold.Hold(coveredFor = yt, misses = 1)))
    }

    @Test
    fun `a front-speaking release is allowed once the hold has genuinely expired`() {
        // The correction still arrives, and the passes are actually run: three readable empty
        // ones end the hold, and the release that follows is not blocked by this rule. Built by
        // aging rather than by naming NONE, because naming NONE is the assumption under test —
        // from the other direction, this is the assertion that would fail if afterPass ever
        // regressed to leaving coveredFor set past [FloatingHold.CONFIRMING_PASSES].
        var hold = FloatingHold.Hold(coveredFor = yt, misses = 0)
        repeat(FloatingHold.CONFIRMING_PASSES) { hold = pass(hold, Answer.None) }
        assertFalse("the passes really did end the hold", hold.active)

        assertTrue(FloatingHold.releaseAllowed(pkg = null, floating = null, hold = hold))
    }

    @Test
    fun `naming an app is still allowed to release that app while a hold stands`() {
        // The loose form is untouched: a grant for the app that is floating goes through, or
        // winning a hand over a float would leave the blocker on the app that just earned it.
        assertTrue(FloatingHold.releaseAllowed(pkg = yt, floating = null, hold = FloatingHold.Hold(coveredFor = yt)))
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

    @Test
    fun `a found answer names its package`() {
        // The only way to read the package out of an Answer is the smart cast the production
        // code uses; there is deliberately no total accessor, because "which app" is not a
        // question the two non-findings can answer.
        assertEquals(yt, Answer.Found(yt).packageName)
    }
}
