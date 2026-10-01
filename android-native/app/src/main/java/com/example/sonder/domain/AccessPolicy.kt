package com.example.sonder.domain

import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.HandOutcome
import java.time.Instant
import java.time.ZoneId

/**
 * Pure bank policy — the timing model locked in with the user:
 *
 * - Access is a **bank** of unspent time per app, and the bank is a **daily allowance**:
 *   it holds [AccessRules.maxMillis] until it has been spent, and every local midnight it
 *   is one again. A target added mid-day starts on that allowance rather than on nothing,
 *   because the allowance is a property of the day rather than of the hand that won it.
 * - **A bet is a chip, and chips come out of the bank.** The player stakes 2:00 / 5:00 /
 *   10:00, or the whole bank, before the hand. Win → `bank += stake`. Lose → `bank -= stake`,
 *   floored at 0. Push → nothing. A stake the bank cannot cover cannot be played
 *   ([canStake]): there is no seat, no free hand and nothing minted out of nothing.
 * - **The gate is on every open of a gated app.** With time in the bank it is the table,
 *   and a won hand is the way in; with none it is a wall. Winning does not spend the bank —
 *   using the app does.
 * - The bank is capped at the app's own maximum ([AccessRules.maxMillis], default 1:00).
 * - **The clock only runs while the app is in front.** Elapsed foreground time is billed at
 *   [MAX_BILL_MILLIS] a step; time away is never billed and never revoked.
 * - The bank belongs to a **local day**: a row stamped with an earlier day has been spent by
 *   definition and reads as the full allowance again ([bankAt]).
 * - The bank reaching zero **locks removal of the target** for [REMOVAL_LOCK_MILLIS], for as
 *   long as that day lasts.
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
     * Whether [stakeMillis] can be played from [bankMillis].
     *
     * The bank backs the bet, without exception. A chip the bank cannot cover is not a bet:
     * a hand played from an empty bank could otherwise be re-bet until it won, at no cost
     * but a deal, and that is time minted rather than won. So an empty bank plays nothing,
     * and the way to time is to still have some.
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
     * Unspent access in a stored bank. A row from another local day reads as the allowance.
     *
     * The day check is what makes the bank a daily allowance without a second table and
     * without an alarm: the refill is this predicate rather than a write. A row stamped with
     * an earlier day is spent by definition — there is nothing in it to carry over — and
     * [maxMillis] is what the new day holds.
     */
    fun bankAt(
        remainingMillis: Long,
        epochDay: Long,
        nowMillis: Long,
        maxMillis: Long = DEFAULT_MAX_MILLIS,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = if (epochDay == epochDayOf(nowMillis, zoneId)) {
        remainingMillis.coerceAtLeast(0L)
    } else {
        maxMillis.coerceAtLeast(0L)
    }

    /**
     * [bankAt] for a package that may have no stored row at all.
     *
     * A package with no row is one that has spent nothing today, so it reads the day's full
     * allowance for the same reason a stale row does. That is what makes a target added a
     * moment ago usable rather than locked until tomorrow: the bank is the day's, and the
     * day started at midnight, not when the user picked the app.
     */
    fun bankFor(
        storedMillis: Long?,
        epochDay: Long?,
        nowMillis: Long,
        maxMillis: Long = DEFAULT_MAX_MILLIS,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = if (storedMillis == null || epochDay == null) {
        maxMillis.coerceAtLeast(0L)
    } else {
        bankAt(storedMillis, epochDay, nowMillis, maxMillis, zoneId)
    }

    /** Epoch millis at which the removal lock for a bank emptied at [emptySinceMillis] ends. */
    fun removalLockedUntil(emptySinceMillis: Long): Long =
        if (emptySinceMillis <= 0L) 0L else emptySinceMillis + REMOVAL_LOCK_MILLIS

    /**
     * True while a target's removal is refused because its bank was drained recently.
     *
     * The lock belongs to the day the bank was drained on. Come midnight the allowance is
     * back, and a lock that outlived it would be punishing a user for an afternoon that the
     * refill has already answered — so a stamp from an earlier local day is spent rather
     * than served.
     */
    fun isRemovalLocked(
        emptySinceMillis: Long,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Boolean =
        emptySinceMillis > 0L &&
            epochDayOf(emptySinceMillis, zoneId) == epochDayOf(nowMillis, zoneId) &&
            nowMillis < removalLockedUntil(emptySinceMillis)

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
