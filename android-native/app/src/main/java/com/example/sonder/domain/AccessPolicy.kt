package com.example.sonder.domain

import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.LockoutSnapshot
import java.time.Instant
import java.time.ZoneId

/**
 * Pure access policy — the debt model locked in with the user (plan §1):
 *
 * - Win with zero debt  → +5:00 access.
 * - Loss                → +10:00 debt, capped at 60:00. You may keep gambling;
 *                         each win pays off 10:00 of debt.
 * - Win with debt       → debt −10:00 (no access granted yet).
 * - Push                → free replay (nothing changes).
 * - Walk away with debt → locked until debt is served (lockout = full debt).
 * - Absence >60s while a grant is active → grant revoked regardless of time left.
 *
 * All timers are absolute epoch millis so process death/reboot never corrupts state.
 */
object AccessPolicy {
    const val WIN_GRANT_MILLIS: Long = 5 * 60_000L        // +5:00 access per win
    const val LOSS_DEBT_MILLIS: Long = 10 * 60_000L       // +10:00 debt per loss
    const val MAX_DEBT_MILLIS: Long = 60 * 60_000L        // user-locked cap: 60:00
    const val ABSENCE_REVOKE_MILLIS: Long = 60_000L       // >60s away revokes the grant

    /**
     * A win resolves debt first; access is only granted from a zero-debt state.
     *
     * [grantedTodayMillis] is the total already granted to this package today; it
     * only matters when [rules] carries a non-null daily cap. When the cap is spent
     * a win grants nothing and returns a cap lockout until the next local midnight.
     */
    fun onHandResult(
        outcome: HandOutcome,
        debtMillis: Long,
        nowMillis: Long,
        rules: AccessRules = AccessRules(),
        grantedTodayMillis: Long = 0L,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): PolicyResult = when (outcome) {
        HandOutcome.PUSH -> PolicyResult(debtMillis = debtMillis, grantedUntil = null)
        HandOutcome.WIN -> {
            val remainingDebt = (debtMillis - rules.lossDebtMillis).coerceAtLeast(0L)
            if (remainingDebt > 0L) {
                // Debt paid down but not cleared: no access yet, keep gambling.
                PolicyResult(debtMillis = remainingDebt, grantedUntil = null)
            } else {
                grantForWin(rules, nowMillis, grantedTodayMillis, zoneId)
            }
        }
        HandOutcome.LOSE -> {
            val newDebt = (debtMillis + rules.lossDebtMillis).coerceAtMost(rules.maxDebtMillis)
            PolicyResult(debtMillis = newDebt, grantedUntil = null)
        }
    }

    /**
     * Resolve a debt-free win. Without a cap this is the plain grant; with one the
     * grant is clamped to the day's remaining allowance and the cap locks the app
     * out until the next local midnight once it is spent.
     */
    private fun grantForWin(
        rules: AccessRules,
        nowMillis: Long,
        grantedTodayMillis: Long,
        zoneId: ZoneId,
    ): PolicyResult {
        val cap = rules.dailyCapMillis ?: return PolicyResult(debtMillis = 0L, grantedUntil = nowMillis + rules.winGrantMillis)
        val remaining = cap - grantedTodayMillis
        if (remaining <= 0L) {
            return PolicyResult(
                debtMillis = 0L,
                grantedUntil = null,
                capLockoutUntil = nextLocalMidnight(nowMillis, zoneId),
            )
        }
        val granted = minOf(rules.winGrantMillis, remaining)
        val capLockout = if (granted >= remaining) nextLocalMidnight(nowMillis, zoneId) else null
        return PolicyResult(
            debtMillis = 0L,
            grantedUntil = nowMillis + granted,
            capLockoutUntil = capLockout,
        )
    }

    /** Lockout end when the user walks away carrying debt. */
    fun lockoutUntil(debtMillis: Long, nowMillis: Long): Long = nowMillis + debtMillis

    /** True if a fresh window event shows the user returned after too long an absence. */
    fun shouldRevokeForAbsence(
        grant: GrantSnapshot,
        nowMillis: Long,
        rules: AccessRules = AccessRules(),
    ): Boolean = nowMillis - grant.lastSeenMillis > rules.absenceRevokeMillis

    /** Epoch millis of the next local midnight after [nowMillis]. */
    fun nextLocalMidnight(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

    /** True if a grant is still valid at [nowMillis]. */
    fun isGrantActive(grant: GrantSnapshot, nowMillis: Long): Boolean = grant.endAtMillis > nowMillis

    /** True if the user may open the blackjack table for the target right now. */
    fun canPlay(lockout: LockoutSnapshot?, nowMillis: Long): Boolean =
        lockout == null || lockout.untilMillis <= nowMillis

    /**
     * True once debt has reached the ceiling the app allows.
     *
     * Below the ceiling the table stays open, because winning hands are the fast way to pay
     * debt down. At the ceiling there is nothing left to pay down that way — every further
     * loss just writes the same lockout — so the gate stops offering hands and the debt is
     * served by waiting instead (the repository clears it once its lockout has run out).
     * Leaving the table open there would be an unbounded session: the player could play for
     * as long as they liked, which is the opposite of what this app is for.
     */
    fun isDebtAtCap(debtMillis: Long, maxDebtMillis: Long = MAX_DEBT_MILLIS): Boolean =
        debtMillis >= maxDebtMillis

    /** Overall enforcement state for a target at a moment in time. */
    fun stateFor(
        packageName: String,
        enabled: Boolean,
        grant: GrantSnapshot?,
        lockout: LockoutSnapshot?,
        nowMillis: Long,
    ): EnforcementState = when {
        !enabled -> EnforcementState.DISABLED
        grant != null && isGrantActive(grant, nowMillis) -> EnforcementState.GRANTED
        lockout != null && lockout.untilMillis > nowMillis -> EnforcementState.LOCKED
        else -> EnforcementState.IDLE
    }
}

data class PolicyResult(
    val debtMillis: Long,
    val grantedUntil: Long?,
    /** Set when the daily cap is spent: locked out until this epoch millis. */
    val capLockoutUntil: Long? = null,
)
