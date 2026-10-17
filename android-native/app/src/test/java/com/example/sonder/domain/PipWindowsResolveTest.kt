package com.example.sonder.domain

import com.example.sonder.platform.accessibility.PipWindows
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rule that turns what the window probe *read* into what it *means*.
 *
 * [PipWindows.resolve] is the separable half of the service's probe: the platform reads — the
 * window list, the bounds test, each candidate's root — happen on the device, and this is the
 * decision made from them. Null in the roots list is a small window whose root would not
 * resolve, which is the value the device produces and a test can produce anywhere.
 */
class PipWindowsResolveTest {

    private val yt = "com.google.android.youtube"
    private val insta = "com.instagram.android"

    private fun resolve(vararg roots: String?) =
        PipWindows.resolve(packageNames = listOf(yt, insta), roots = roots.toList())

    @Test
    fun `a window that resolved names its package and leaves the other a no`() {
        val answers = resolve(yt)

        assertEquals(FloatingHold.Answer.Found(yt), answers[yt])
        assertEquals(FloatingHold.Answer.None, answers[insta])
    }

    @Test
    fun `no small windows at all is a readable no for every package`() {
        val answers = resolve()

        assertEquals(FloatingHold.Answer.None, answers[yt])
        assertEquals(FloatingHold.Answer.None, answers[insta])
    }

    @Test
    fun `an unreadable window makes every unfound package unreadable too`() {
        // The window that would not resolve could have belonged to either of them, so neither
        // may be told "no" — a "no" that had been a guess is what let the hold age out over a
        // float nobody managed to look at.
        val answers = resolve(null)

        assertEquals(FloatingHold.Answer.Unknown, answers[yt])
        assertEquals(FloatingHold.Answer.Unknown, answers[insta])
    }

    @Test
    fun `a package that was found stays found even when another window was not`() {
        // The ordering this rule exists for: blindness must not demote a positive answer. Read
        // the other way round, a genuinely floating target would be reported unreported AND
        // unreadable, which is the pair that frees a caller to release over it.
        val answers = resolve(yt, null)

        assertEquals(FloatingHold.Answer.Found(yt), answers[yt])
        assertEquals(FloatingHold.Answer.Unknown, answers[insta])
    }

    @Test
    fun `every requested package is answered, so none can be read as absent by omission`() {
        val answers = PipWindows.resolve(packageNames = listOf(yt, insta), roots = listOf(yt))

        assertEquals(setOf(yt, insta), answers.keys)
    }

    @Test
    fun `the same window named twice does not disturb the answer`() {
        val answers = resolve(yt, yt)

        assertEquals(FloatingHold.Answer.Found(yt), answers[yt])
        assertEquals(FloatingHold.Answer.None, answers[insta])
    }

    @Test
    fun `a window belonging to no requested package answers no for all of them`() {
        // Some other app's small window is not evidence about these two, and it is readable:
        // the probe saw it and it was not theirs, which is exactly a "no" they can be told.
        val answers = resolve("com.example.something.else")

        assertEquals(FloatingHold.Answer.None, answers[yt])
        assertEquals(FloatingHold.Answer.None, answers[insta])
    }

    @Test
    fun `an empty question answers nothing rather than guessing`() {
        assertEquals(emptyMap<String, FloatingHold.Answer>(), PipWindows.resolve(emptyList(), emptyList()))
    }
}
