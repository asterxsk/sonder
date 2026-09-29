package com.example.sonder.ui.screens.targets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The delay machinery, pure: the hold is a property of the kind, the readout floors at
 * zero, and the reducer returns null exactly when the wait is spent — the screen's
 * signal to fire.
 */
class DelayedTargetActionTest {

    @Test
    fun `each kind carries its own hold`() {
        assertEquals(15_000L, TargetActionKind.EDIT.holdMillis)
        assertEquals(30_000L, TargetActionKind.REMOVE.holdMillis)
    }

    @Test
    fun `a fresh action starts at its kind's hold`() {
        assertEquals(30_000L, startPendingAction("com.example.a", TargetActionKind.REMOVE).remainingMillis)
        assertEquals(15_000L, startPendingAction("com.example.a", TargetActionKind.EDIT).remainingMillis)
    }

    @Test
    fun `remaining seconds floors and never goes below zero`() {
        assertEquals(24L, PendingTargetAction("a", TargetActionKind.REMOVE, 24_500L).remainingSeconds())
        assertEquals(0L, PendingTargetAction("a", TargetActionKind.REMOVE, 999L).remainingSeconds())
        assertEquals(0L, PendingTargetAction("a", TargetActionKind.REMOVE, -50L).remainingSeconds())
    }

    @Test
    fun `the label reads in the REMOVE 0 colon 24 form`() {
        assertEquals(
            "REMOVE 0:24",
            PendingTargetAction("a", TargetActionKind.REMOVE, 24_500L).label(),
        )
        assertEquals(
            "EDIT 0:15",
            startPendingAction("a", TargetActionKind.EDIT).label(),
        )
        // Past a minute the minutes carry no leading zero, like the app's other clocks.
        assertEquals(
            "REMOVE 1:05",
            PendingTargetAction("a", TargetActionKind.REMOVE, 65_000L).label(),
        )
    }

    @Test
    fun `the reducer ticks a second off and returns null at the end of the hold`() {
        val afterOne = startPendingAction("a", TargetActionKind.EDIT).tick()
        assertEquals(14_000L, afterOne?.remainingMillis)

        // The last step of the hold consumes the final second and fires: null, not 0.
        assertNull(PendingTargetAction("a", TargetActionKind.EDIT, 1_000L).tick())
    }

    @Test
    fun `ticking a full hold exactly fires it`() {
        var state: PendingTargetAction? = startPendingAction("a", TargetActionKind.REMOVE)
        var ticks = 0
        while (state != null) {
            ticks++
            state = state.tick()
        }
        // 30 s of 1 s ticks, ending on the tick that returns null.
        assertEquals(30, ticks)
    }
}
