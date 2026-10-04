package com.example.sonder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessSessionTest {
    private val t0 = 1_700_000_000_000L

    @Test
    fun `a won chip sets the exact usable duration`() {
        val twoMinutes = AccessSession("app", 2 * 60_000L)
        val fiveMinutes = AccessSession("app", 5 * 60_000L)

        assertEquals(2 * 60_000L, twoMinutes.remainingMillis)
        assertEquals(5 * 60_000L, fiveMinutes.remainingMillis)
    }

    @Test
    fun `only consumed foreground time reduces the session`() {
        val session = AccessSession("app", 5 * 60_000L)
            .consume(30_000L)
            .leave(t0)

        assertEquals(4 * 60_000L + 30_000L, session.remainingMillis)
        assertTrue(session.isAvailableFor("app", t0 + 59_999L))
    }

    @Test
    fun `sixty seconds away expires the session without consuming its remainder`() {
        val session = AccessSession("app", 5 * 60_000L)
            .consume(30_000L)
            .leave(t0)

        assertFalse(session.isAvailableFor("app", t0 + 60_000L))
        assertEquals(4 * 60_000L + 30_000L, session.remainingMillis)
    }

    @Test
    fun `returning during grace resumes the same session`() {
        val resumed = AccessSession("app", 5 * 60_000L)
            .consume(30_000L)
            .leave(t0)
            .resume()

        assertTrue(resumed.isAvailableFor("app", t0 + 60_000L))
        assertEquals(4 * 60_000L + 30_000L, resumed.remainingMillis)
    }
}
