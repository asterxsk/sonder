package com.example.sonder.domain

import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.LockoutSnapshot

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

    /** A win resolves debt first; access is only granted from a zero-debt state. */
    fun onHandResult(
        outcome: HandOutcome,
        debtMillis: Long,
        nowMillis: Long,
    ): PolicyResult = when (outcome) {
        HandOutcome.PUSH -> PolicyResult(debtMillis = debtMillis, grantedUntil = null)
        HandOutcome.WIN -> {
            val remainingDebt = (debtMillis - LOSS_DEBT_MILLIS).coerceAtLeast(0L)
            if (remainingDebt > 0L) {
                // Debt paid down but not cleared: no access yet, keep gambling.
                PolicyResult(debtMillis = remainingDebt, grantedUntil = null)
            } else {
                PolicyResult(debtMillis = 0L, grantedUntil = nowMillis + WIN_GRANT_MILLIS)
            }
        }
        HandOutcome.LOSE -> {
            val newDebt = (debtMillis + LOSS_DEBT_MILLIS).coerceAtMost(MAX_DEBT_MILLIS)
            PolicyResult(debtMillis = newDebt, grantedUntil = null)
        }
    }

    /** Lockout end when the user walks away carrying debt. */
    fun lockoutUntil(debtMillis: Long, nowMillis: Long): Long = nowMillis + debtMillis

    /** True if a fresh window event shows the user returned after too long an absence. */
    fun shouldRevokeForAbsence(grant: GrantSnapshot, nowMillis: Long): Boolean =
        nowMillis - grant.lastSeenMillis > ABSENCE_REVOKE_MILLIS

    /** True if a grant is still valid at [nowMillis]. */
    fun isGrantActive(grant: GrantSnapshot, nowMillis: Long): Boolean = grant.endAtMillis > nowMillis

    /** True if the user may open the blackjack table for the target right now. */
    fun canPlay(lockout: LockoutSnapshot?, nowMillis: Long): Boolean =
        lockout == null || lockout.untilMillis <= nowMillis

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
)
