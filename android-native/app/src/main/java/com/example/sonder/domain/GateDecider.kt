package com.example.sonder.domain

import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot

/** What the coordinator must do for one foreground window event. */
enum class GateDecision {
    /** The package is not an enabled target — release any blocker. */
    PASS,

    /** Active grant and no absence violation — let the app run, touch last-seen. */
    GRANTED,

    /** The grant is stale (user was away too long) — revoke and gate. */
    REVOKE,

    /** Enabled target currently serving a lockout — lockout blocker, no table. */
    LOCKOUT,

    /** Enabled target, no grant, not locked out — raise the blocker (blackjack table). */
    GATE,
}

/**
 * Pure per-event decision core (no Android, no persistence): given the cached
 * enforcement state of one package at the moment it reaches the foreground,
 * decide what the platform layer must do. Unit-tested so the coordinator and
 * the overlay host stay thin.
 */
object GateDecider {

    /**
     * @param targetEnabled package is an enabled target
     * @param grant         current grant row, or null
     * @param lockout       current lockout row, or null
     */
    fun decide(
        targetEnabled: Boolean,
        grant: GrantSnapshot?,
        lockout: LockoutSnapshot?,
        nowMillis: Long,
    ): GateDecision {
        if (!targetEnabled) return GateDecision.PASS

        if (grant != null && AccessPolicy.isGrantActive(grant, nowMillis)) {
            return if (AccessPolicy.shouldRevokeForAbsence(grant, nowMillis)) {
                GateDecision.REVOKE
            } else {
                GateDecision.GRANTED
            }
        }

        // No active grant here — either none at all or an expired row (the caller
        // purges expired rows so enforcement resumes). An active lockout wins.
        if (lockout != null && lockout.untilMillis > nowMillis) return GateDecision.LOCKOUT

        return GateDecision.GATE
    }
}
