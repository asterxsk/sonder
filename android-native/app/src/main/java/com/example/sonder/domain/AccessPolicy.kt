package com.example.sonder.domain

import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.HandOutcome
import java.time.Instant
import java.time.ZoneId

/**
 * Pure bank policy — the timing model locked in with the user:
 *
 * - Access is a **bank** of unspent time per app, starting at zero.
 * - The player stakes a chip (2:00 / 5:00 / 10:00, or the whole bank) before the hand.
 *   Win → `bank += stake`. Lose → `bank -= stake`, floored at 0. Push → nothing.
 * - The bank is capped at the app's own maximum ([AccessRules.maxMillis], default 1:00).
 * - **The clock only runs while the app is in front.** Elapsed foreground time is billed at
 *   [MAX_BILL_MILLIS] a step; time away is never billed and never revoked. This is what
 *   replaced the old absolute `endAtMillis` grant, which ran down while the user was
 *   elsewhere and was destroyed outright by a long enough absence.
 * - The bank belongs to a **local day**. A read on a later day reads zero, which is the
 *   daily cap the old `daily_usage` table used to enforce: every day starts gated.
 * - The bank reaching zero **locks removal of the target** for [REMOVAL_LOCK_MILLIS].
 *
 * All timers are absolute epoch millis so process death/reboot never corrupts state.
 */
object AccessPolicy {
    /** Default ceiling on the bank: one hour. */
    const val DEFAULT_MAX_MILLIS: Long = 60 * 60_000L

    /** How long removal of a target is refused once its bank has been drained. */
    const val REMOVAL_LOCK_MILLIS: Long = 12 * 60 * 60_000L

    /**
     * Most foreground time one billing step may charge.
     *
     * The drain rides on the coordinator's last-seen heartbeat, so the gap it measures is
     * normally a few seconds. A process death, a reboot, or a stalled service can leave a
     * much larger one behind, and a bank that is billed for a gap nobody was present for is
     * the wall-clock behaviour this model exists to remove.
     */
    const val MAX_BILL_MILLIS: Long = 10_000L

    /**
     * The fixed chips a player may stake. ALL IN is not one of them — it is the whole bank.
     *
     * A chip is staked at face value even when the bank holds less, which is what makes the
     * table playable from an empty bank: a loss floors at zero, so the player risks only the
     * time they do not have, and a win is the way back in.
     */
    val CHIPS: List<Long> = listOf(2 * 60_000L, 5 * 60_000L, 10 * 60_000L)

    /**
     * The bank a settled hand leaves behind.
     *
     * A win credits the stake and clamps at the ceiling; a loss debits it and floors at zero.
     * A push is inert. [stakeMillis] is the whole bank for an all-in, so an all-in loss
     * empties it and an all-in win doubles it back up against the same ceiling.
     *
     * Nothing here is clamped against the ceiling on the way down: a bank already above it
     * (a ceiling lowered after the fact) can only be brought back under by play, and clamping
     * a loss to the ceiling would *credit* time to a player who just lost.
     */
    fun onHandResult(
        outcome: HandOutcome,
        bankMillis: Long,
        stakeMillis: Long,
        maxMillis: Long = DEFAULT_MAX_MILLIS,
    ): Long = when (outcome) {
        HandOutcome.PUSH -> bankMillis
        HandOutcome.WIN -> (bankMillis + stakeMillis).coerceAtMost(maxMillis)
        HandOutcome.LOSE -> (bankMillis - stakeMillis).coerceAtLeast(0L)
    }

    /**
     * The bank after [elapsedMillis] of foreground use.
     *
     * The elapsed is clamped to [0, MAX_BILL_MILLIS] rather than trusted: a clock that moved
     * backwards, a gap left by a dead process, and a reboot all arrive here as a number that
     * is not "how long the user was in the app".
     */
    fun bill(remainingMillis: Long, elapsedMillis: Long): Long =
        (remainingMillis - elapsedMillis.coerceIn(0L, MAX_BILL_MILLIS)).coerceAtLeast(0L)

    /**
     * Whether the bank holds time, reading a bank from an earlier local day as empty.
     *
     * The day check is what makes the bank a daily allowance without a second table: the row
     * survives midnight, it simply stops counting.
     */
    fun bankAt(remainingMillis: Long, epochDay: Long, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        if (epochDay == epochDayOf(nowMillis, zoneId)) remainingMillis.coerceAtLeast(0L) else 0L

    /** Epoch millis at which the removal lock for a bank emptied at [emptySinceMillis] ends. */
    fun removalLockedUntil(emptySinceMillis: Long): Long =
        if (emptySinceMillis <= 0L) 0L else emptySinceMillis + REMOVAL_LOCK_MILLIS

    /** True while a target's removal is refused because its bank was drained recently. */
    fun isRemovalLocked(emptySinceMillis: Long, nowMillis: Long): Boolean =
        emptySinceMillis > 0L && nowMillis < removalLockedUntil(emptySinceMillis)

    /** Epoch millis of the next local midnight after [nowMillis]. */
    fun nextLocalMidnight(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    /** `LocalDate.toEpochDay()` for the local day containing [nowMillis]. */
    fun epochDayOf(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toEpochDay()

    /**
     * Overall enforcement state for a target at a moment in time.
     *
     * With no debt and no lockout left in the model there are only three answers, and the
     * bank is the whole of the difference between two of them: a non-empty bank *is* the
     * grant.
     */
    fun stateFor(
        enabled: Boolean,
        bankMillis: Long,
    ): EnforcementState = when {
        !enabled -> EnforcementState.DISABLED
        bankMillis > 0L -> EnforcementState.GRANTED
        else -> EnforcementState.IDLE
    }
}
