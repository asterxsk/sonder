package com.example.sonder.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-event decision core. Four answers: not a target passes, a won hand runs, a target
 * with time in its bank but no hand won gets the table, and a target with an empty bank gets
 * the wall. The scope cases are the ones with the subtlety in them — a scoped target is only
 * a target while its own surface is up — and the entry cases carry the rule the gate exists
 * for, which is that holding time is not the same as being let in.
 */
class GateDeciderTest {

    @Test
    fun `a package that is not a target passes`() {
        assertEquals(GateDecision.PASS, decide(targetEnabled = false, bankMillis = 0L))
    }

    @Test
    fun `a package that is not a target passes even with time in its bank`() {
        assertEquals(GateDecision.PASS, decide(targetEnabled = false, bankMillis = 30 * 60_000L))
    }

    @Test
    fun `a target with an empty bank gets the wall`() {
        assertEquals(GateDecision.LOCKED, decide(targetEnabled = true, bankMillis = 0L))
    }

    @Test
    fun `a target with time in its bank but no hand won gets the table`() {
        // The rule the gate is for: holding time is not being let in. This is the case that
        // used to read GRANTED, and it is why an app with a bank still shows blackjack.
        assertEquals(GateDecision.GATE, decide(targetEnabled = true, bankMillis = 1L))
    }

    @Test
    fun `a target the user has won their way into runs`() {
        assertEquals(GateDecision.GRANTED, decide(targetEnabled = true, bankMillis = 1L, entered = true))
    }

    @Test
    fun `a target with a full bank still gets the table until a hand is won`() {
        assertEquals(
            GateDecision.GATE,
            decide(targetEnabled = true, bankMillis = AccessPolicy.DEFAULT_MAX_MILLIS),
        )
    }

    /**
     * The bank is read before the entry, so a bank that has emptied under a live grant drops
     * to the wall on the next pass rather than waiting for the user to leave and come back.
     */
    @Test
    fun `an entered target whose bank has emptied gets the wall`() {
        assertEquals(GateDecision.LOCKED, decide(targetEnabled = true, bankMillis = 0L, entered = true))
    }

    /**
     * A stale negative — a debt row from a build that had one, a bank written before a clamp
     * existed — must lock rather than pass or offer a table there is nothing to back.
     */
    @Test
    fun `a negative bank is locked`() {
        assertEquals(GateDecision.LOCKED, decide(targetEnabled = true, bankMillis = -1L))
        assertEquals(
            GateDecision.LOCKED,
            decide(targetEnabled = true, bankMillis = -1L, entered = true),
        )
    }

    // --- scope -----------------------------------------------------------------

    @Test
    fun `a shorts-scoped target passes anywhere that is not the short-form surface`() {
        // The whole point of the scope: the app is a target, but the feed is not a gate.
        assertEquals(
            GateDecision.PASS,
            decide(
                targetEnabled = true,
                bankMillis = 0L,
                blockScope = BlockScope.SHORTS_ONLY,
                scopedSurfacePresent = false,
            ),
        )
    }

    @Test
    fun `a shorts-scoped target walls on its short-form surface`() {
        assertEquals(
            GateDecision.LOCKED,
            decide(
                targetEnabled = true,
                bankMillis = 0L,
                blockScope = BlockScope.SHORTS_ONLY,
                scopedSurfacePresent = true,
            ),
        )
    }

    @Test
    fun `a scoped-surface pass outranks a banked balance`() {
        // Not the same as spending it: the bank is untouched and still there for the surface
        // this target does gate. It simply has nothing to authorise on a screen the target was
        // never about, so winning a hand to watch Reels cannot open the feed.
        assertEquals(
            GateDecision.PASS,
            decide(
                targetEnabled = true,
                bankMillis = 30 * 60_000L,
                blockScope = BlockScope.SHORTS_ONLY,
                scopedSurfacePresent = false,
            ),
        )
    }

    @Test
    fun `a shorts-scoped target with time gets the table on its surface`() {
        assertEquals(
            GateDecision.GATE,
            decide(
                targetEnabled = true,
                bankMillis = 30 * 60_000L,
                blockScope = BlockScope.SHORTS_ONLY,
                scopedSurfacePresent = true,
            ),
        )
    }

    @Test
    fun `a shorts-scoped target the user won into runs on its surface`() {
        assertEquals(
            GateDecision.GRANTED,
            decide(
                targetEnabled = true,
                bankMillis = 30 * 60_000L,
                blockScope = BlockScope.SHORTS_ONLY,
                scopedSurfacePresent = true,
                entered = true,
            ),
        )
    }

    @Test
    fun `a whole-app target ignores the surface probe`() {
        // scopedSurfacePresent is meaningless for WHOLE_APP, and the default is what every
        // caller without a probe gets: passing false there must not become a free pass.
        assertEquals(
            GateDecision.LOCKED,
            decide(targetEnabled = true, bankMillis = 0L, scopedSurfacePresent = false),
        )
    }

    @Test
    fun `a disabled shorts target still passes before the scope is consulted`() {
        assertEquals(
            GateDecision.PASS,
            decide(
                targetEnabled = false,
                bankMillis = 0L,
                blockScope = BlockScope.SHORTS_ONLY,
                scopedSurfacePresent = true,
            ),
        )
    }

    private fun decide(
        targetEnabled: Boolean,
        bankMillis: Long,
        blockScope: BlockScope = BlockScope.WHOLE_APP,
        scopedSurfacePresent: Boolean = true,
        entered: Boolean = false,
    ): GateDecision = GateDecider.decide(
        targetEnabled = targetEnabled,
        bankMillis = bankMillis,
        blockScope = blockScope,
        scopedSurfacePresent = scopedSurfacePresent,
        entered = entered,
    )
}
