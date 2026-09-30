package com.example.sonder.domain

import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.HandOutcome
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bank policy, which is now the whole timing model: no debt, no lockout, no wall-clock
 * grant. Three things are worth testing here and they are the three the app's behaviour hangs
 * on — what a hand does to the bank, what foreground use does to it, and when its owner may
 * still delete it.
 */
class AccessPolicyTest {

    private val utc = ZoneId.of("UTC")
    private val noon = 1_700_000_000_000L

    // --- hands -----------------------------------------------------------------

    @Test
    fun `a win credits the stake`() {
        assertEquals(
            7 * 60_000L,
            AccessPolicy.onHandResult(HandOutcome.WIN, bankMillis = 2 * 60_000L, stakeMillis = 5 * 60_000L),
        )
    }

    @Test
    fun `a loss debits the stake`() {
        assertEquals(
            3 * 60_000L,
            AccessPolicy.onHandResult(HandOutcome.LOSE, bankMillis = 8 * 60_000L, stakeMillis = 5 * 60_000L),
        )
    }

    @Test
    fun `a loss below the stake floors at zero rather than going negative`() {
        assertEquals(
            0L,
            AccessPolicy.onHandResult(HandOutcome.LOSE, bankMillis = 60_000L, stakeMillis = 10 * 60_000L),
        )
    }

    @Test
    fun `a push is inert`() {
        assertEquals(
            4 * 60_000L,
            AccessPolicy.onHandResult(HandOutcome.PUSH, bankMillis = 4 * 60_000L, stakeMillis = 10 * 60_000L),
        )
    }

    @Test
    fun `a win stops at the ceiling`() {
        assertEquals(
            60 * 60_000L,
            AccessPolicy.onHandResult(
                HandOutcome.WIN,
                bankMillis = 58 * 60_000L,
                stakeMillis = 10 * 60_000L,
                maxMillis = 60 * 60_000L,
            ),
        )
    }

    @Test
    fun `an all-in win doubles the bank back up to the ceiling`() {
        assertEquals(
            30 * 60_000L,
            AccessPolicy.onHandResult(
                HandOutcome.WIN,
                bankMillis = 15 * 60_000L,
                stakeMillis = 15 * 60_000L,
                maxMillis = 60 * 60_000L,
            ),
        )
    }

    @Test
    fun `an all-in loss empties the bank`() {
        assertEquals(
            0L,
            AccessPolicy.onHandResult(
                HandOutcome.LOSE,
                bankMillis = 15 * 60_000L,
                stakeMillis = 15 * 60_000L,
            ),
        )
    }

    /**
     * The ceiling is a lid, not a floor. A bank left above it by a ceiling that was lowered
     * afterwards must be brought back down by play — clamping the loss to the ceiling here
     * would credit time to a player who just lost it.
     */
    @Test
    fun `a loss is not clamped up to the ceiling`() {
        assertEquals(
            50 * 60_000L,
            AccessPolicy.onHandResult(
                HandOutcome.LOSE,
                bankMillis = 55 * 60_000L,
                stakeMillis = 5 * 60_000L,
                maxMillis = 30 * 60_000L,
            ),
        )
    }

    @Test
    fun `a chip stakes at face value even from an empty bank`() {
        // The table has to be playable from nothing: a loss floors, so the player risks only
        // time they do not have, and the win is the way back in.
        assertEquals(
            2 * 60_000L,
            AccessPolicy.onHandResult(HandOutcome.WIN, bankMillis = 0L, stakeMillis = AccessPolicy.CHIPS.first()),
        )
    }

    @Test
    fun `the chips are two, five and ten minutes`() {
        assertEquals(listOf(2 * 60_000L, 5 * 60_000L, 10 * 60_000L), AccessPolicy.CHIPS)
    }

    // --- billing ---------------------------------------------------------------

    @Test
    fun `billing subtracts the elapsed foreground time`() {
        // Five seconds, not five minutes: a real billing step is the gap since the last stamp,
        // which the coordinator refreshes every five seconds while the target is in front. A
        // five-*minute* gap is the clamp case below, not this one.
        assertEquals(30 * 60_000L - 5_000L, AccessPolicy.bill(remainingMillis = 30 * 60_000L, elapsedMillis = 5_000L))
    }

    @Test
    fun `billing floors at zero rather than going negative`() {
        assertEquals(0L, AccessPolicy.bill(remainingMillis = 1_000L, elapsedMillis = 5 * 60_000L))
    }

    @Test
    fun `billing never charges more than one step`() {
        // A reboot, a dead process or a stalled service can leave an hour-wide gap behind.
        // None of it is time the user was in the app, and none of it is billed.
        assertEquals(
            30 * 60_000L - AccessPolicy.MAX_BILL_MILLIS,
            AccessPolicy.bill(remainingMillis = 30 * 60_000L, elapsedMillis = 60 * 60_000L),
        )
    }

    @Test
    fun `billing ignores a clock that moved backwards`() {
        assertEquals(30 * 60_000L, AccessPolicy.bill(remainingMillis = 30 * 60_000L, elapsedMillis = -5_000L))
    }

    @Test
    fun `billing exactly one step is allowed`() {
        assertEquals(
            30 * 60_000L - AccessPolicy.MAX_BILL_MILLIS,
            AccessPolicy.bill(remainingMillis = 30 * 60_000L, elapsedMillis = AccessPolicy.MAX_BILL_MILLIS),
        )
    }

    // --- the day ---------------------------------------------------------------

    @Test
    fun `a bank from today is read as it stands`() {
        assertEquals(
            12 * 60_000L,
            AccessPolicy.bankAt(
                remainingMillis = 12 * 60_000L,
                epochDay = AccessPolicy.epochDayOf(noon, utc),
                nowMillis = noon,
                zoneId = utc,
            ),
        )
    }

    @Test
    fun `a bank from an earlier day reads as nothing`() {
        // What the deleted daily_usage table used to do: every day starts gated.
        assertEquals(
            0L,
            AccessPolicy.bankAt(
                remainingMillis = 12 * 60_000L,
                epochDay = AccessPolicy.epochDayOf(noon, utc) - 1,
                nowMillis = noon,
                zoneId = utc,
            ),
        )
    }

    @Test
    fun `a bank from a later day reads as nothing`() {
        // A clock set back a day is the same problem in the other direction.
        assertEquals(
            0L,
            AccessPolicy.bankAt(
                remainingMillis = 12 * 60_000L,
                epochDay = AccessPolicy.epochDayOf(noon, utc) + 1,
                nowMillis = noon,
                zoneId = utc,
            ),
        )
    }

    @Test
    fun `a negative stored bank reads as nothing`() {
        assertEquals(
            0L,
            AccessPolicy.bankAt(
                remainingMillis = -1L,
                epochDay = AccessPolicy.epochDayOf(noon, utc),
                nowMillis = noon,
                zoneId = utc,
            ),
        )
    }

    @Test
    fun `the next local midnight is the start of tomorrow`() {
        val midnight = AccessPolicy.nextLocalMidnight(noon, utc)
        assertTrue(midnight > noon)
        assertEquals(AccessPolicy.epochDayOf(noon, utc) + 1, AccessPolicy.epochDayOf(midnight, utc))
        assertEquals(0, midnight % 1_000L)
    }

    // --- the removal lock ------------------------------------------------------

    @Test
    fun `a bank that never emptied is not locked`() {
        assertFalse(AccessPolicy.isRemovalLocked(emptySinceMillis = 0L, nowMillis = noon))
        assertEquals(0L, AccessPolicy.removalLockedUntil(0L))
    }

    @Test
    fun `a drained bank locks removal for twelve hours`() {
        assertTrue(AccessPolicy.isRemovalLocked(emptySinceMillis = noon, nowMillis = noon + 1_000L))
        assertEquals(noon + AccessPolicy.REMOVAL_LOCK_MILLIS, AccessPolicy.removalLockedUntil(noon))
    }

    @Test
    fun `the lock is exclusive at exactly twelve hours`() {
        val ends = noon + AccessPolicy.REMOVAL_LOCK_MILLIS
        assertFalse(AccessPolicy.isRemovalLocked(emptySinceMillis = noon, nowMillis = ends))
        assertTrue(AccessPolicy.isRemovalLocked(emptySinceMillis = noon, nowMillis = ends - 1))
    }

    @Test
    fun `the lock is twelve hours long`() {
        assertEquals(12 * 60 * 60_000L, AccessPolicy.REMOVAL_LOCK_MILLIS)
    }

    // --- state -----------------------------------------------------------------

    @Test
    fun `a disabled target is DISABLED whatever the bank holds`() {
        assertEquals(EnforcementState.DISABLED, AccessPolicy.stateFor(enabled = false, bankMillis = 60_000L))
        assertEquals(EnforcementState.DISABLED, AccessPolicy.stateFor(enabled = false, bankMillis = 0L))
    }

    @Test
    fun `an enabled target with time is GRANTED`() {
        assertEquals(EnforcementState.GRANTED, AccessPolicy.stateFor(enabled = true, bankMillis = 1L))
    }

    @Test
    fun `an enabled target with nothing is IDLE`() {
        assertEquals(EnforcementState.IDLE, AccessPolicy.stateFor(enabled = true, bankMillis = 0L))
    }

    @Test
    fun `the default ceiling is one hour`() {
        assertEquals(60 * 60_000L, AccessPolicy.DEFAULT_MAX_MILLIS)
    }

    @Test
    fun `one billing step is ten seconds`() {
        assertEquals(10_000L, AccessPolicy.MAX_BILL_MILLIS)
    }
}
