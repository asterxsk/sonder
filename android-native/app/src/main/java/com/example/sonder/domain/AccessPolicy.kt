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
 * - **A bet is backed by the bank**: a stake the bank cannot cover cannot be played, so a
 *   win is always paid out of something that was at risk ([canStake]). An empty bank has
 *   nothing to bet and no hand to play until the day opens it again.
 * - The bank is capped at the app's own maximum ([AccessRules.maxMillis], default 1:00).
 * - **The clock only runs while the app is in front.** Elapsed foreground time is billed at
 *   [MAX_BILL_MILLIS] a step; time away is never billed and never revoked. This is what
 *   replaced the old absolute `endAtMillis` grant, which ran down while the user was
 *   elsewhere and was destroyed outright by a long enough absence.
 * - The bank belongs to a **local day**. A read on a later day reads
 *   [OPENING_STAKE_MILLIS], which is the daily cap the old `daily_usage` table used to
 *   enforce: yesterday's leftovers are not today's access, and today still opens with
 *   something to bet.
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
     * A chip is only playable when the bank covers it ([canStake]): the bet is drawn on the
     * access the player actually holds, so the time a win pays out is time that was at risk.
     * A stake the bank cannot cover is not a bet, it is a way of minting time out of nothing —
     * an empty bank could otherwise be rolled on the 10:00 chip until it won, at no cost but
     * a hand's worth of dealing.
     */
    val CHIPS: List<Long> = listOf(2 * 60_000L, 5 * 60_000L, 10 * 60_000L)

    /**
     * The stake a bank opens with at the start of each local day.
     *
     * A chip has to be backed, so a bank reading zero is a table with no chip on it at all:
     * greyed chips, no hand, and no hand to win one with. This is what keeps that from being
     * permanent — the day rolls over and the bank comes back holding the smallest chip, one
     * hand's worth of a way back in. It is also the whole of the day's allowance that arrives
     * unwon, which is deliberately the least a hand can be played for.
     */
    val OPENING_STAKE_MILLIS: Long = CHIPS.first()

    /**
     * Whether [stakeMillis] can be played from [bankMillis].
     *
     * The bank backs the bet. Zero backs nothing, so an empty bank is a table with no seat at
     * it until the day opens it again.
     */
    fun canStake(bankMillis: Long, stakeMillis: Long): Boolean =
        stakeMillis > 0L && bankMillis >= stakeMillis

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
     * Unspent access in a stored bank, reading a row from another local day as the day's
     * opening stake rather than as the time it was left holding.
     *
     * The day check is what makes the bank a daily allowance without a second table: the row
     * survives midnight and is read at [OPENING_STAKE_MILLIS] instead of at what it stored,
     * because yesterday's leftovers are not today's access — and because a day that opened at
     * zero would be a day with no stake to play and no hand to win with.
     */
    fun bankAt(remainingMillis: Long, epochDay: Long, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        if (epochDay == epochDayOf(nowMillis, zoneId)) remainingMillis.coerceAtLeast(0L) else OPENING_STAKE_MILLIS

    /**
     * [bankAt] for a package that has no stored row at all.
     *
     * A package that has never played has no row, and reads as the day's opening stake for
     * the same reason a stale row does: the table is opened by the day it is played on, so a
     * target added this morning is playable this morning rather than sitting at a zero no
     * hand could ever be dealt from.
     */
    fun bankFor(
        storedMillis: Long?,
        epochDay: Long?,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = if (storedMillis == null || epochDay == null) {
        OPENING_STAKE_MILLIS
    } else {
        bankAt(storedMillis, epochDay, nowMillis, zoneId)
    }

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
