package com.example.sonder.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The per-event decision core. Three inputs, three answers: not a target passes, a target with
 * time in its bank runs, a target with nothing gets the table. The scope cases are the ones
 * with the subtlety in them — a scoped target is only a target while its own surface is up.
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
    fun `a target with an empty bank is gated`() {
        assertEquals(GateDecision.GATE, decide(targetEnabled = true, bankMillis = 0L))
    }

    @Test
    fun `a target with any time at all is granted`() {
        assertEquals(GateDecision.GRANTED, decide(targetEnabled = true, bankMillis = 1L))
    }

    @Test
    fun `a target with a full bank is granted`() {
        assertEquals(
            GateDecision.GRANTED,
            decide(targetEnabled = true, bankMillis = AccessPolicy.DEFAULT_MAX_MILLIS),
        )
    }

    /**
     * The bank is read before the decision, so a stale negative — a debt row from a build that
     * had one, a bank written before a clamp existed — must still gate rather than pass.
     */
    @Test
    fun `a negative bank is gated`() {
        assertEquals(GateDecision.GATE, decide(targetEnabled = true, bankMillis = -1L))
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
    fun `a shorts-scoped target gates on its short-form surface`() {
        assertEquals(
            GateDecision.GATE,
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
    fun `a shorts-scoped target with time still runs on its surface`() {
        assertEquals(
            GateDecision.GRANTED,
            decide(
                targetEnabled = true,
                bankMillis = 30 * 60_000L,
                blockScope = BlockScope.SHORTS_ONLY,
                scopedSurfacePresent = true,
            ),
        )
    }

    @Test
    fun `a whole-app target ignores the surface probe`() {
        // scopedSurfacePresent is meaningless for WHOLE_APP, and the default is what every
        // caller without a probe gets: passing false there must not become a free pass.
        assertEquals(
            GateDecision.GATE,
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
    ): GateDecision = GateDecider.decide(
        targetEnabled = targetEnabled,
        bankMillis = bankMillis,
        blockScope = blockScope,
        scopedSurfacePresent = scopedSurfacePresent,
    )
}
