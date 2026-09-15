package com.example.sonder.domain

import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.HandOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The debt-model policy tests — user's L,L,W,W,W example and every edge case. */
class AccessPolicyTest {

    private val t0 = 1_000_000L
    private val win5 = AccessPolicy.WIN_GRANT_MILLIS
    private val loss10 = AccessPolicy.LOSS_DEBT_MILLIS

    @Test
    fun `win at zero debt grants five minutes`() {
        val r = AccessPolicy.onHandResult(HandOutcome.WIN, 0, t0)
        assertEquals(0, r.debtMillis)
        assertEquals(t0 + win5, r.grantedUntil)
    }

    @Test
    fun `loss adds ten minutes of debt`() {
        val r = AccessPolicy.onHandResult(HandOutcome.LOSE, 0, t0)
        assertEquals(loss10, r.debtMillis)
        assertNull(r.grantedUntil)
    }

    @Test
    fun `push changes nothing`() {
        val r = AccessPolicy.onHandResult(HandOutcome.PUSH, 17 * 60_000L, t0)
        assertEquals(17 * 60_000L, r.debtMillis)
        assertNull(r.grantedUntil)
    }

    @Test
    fun `user example - two losses then three wins yields access`() {
        // L, L, W, W, W → debt 20 → 10 → 0 → access. Exactly the user's arithmetic.
        var debt = 0L
        var grantedUntil: Long? = null

        listOf(
            HandOutcome.LOSE, HandOutcome.LOSE, // debt 20
            HandOutcome.WIN, HandOutcome.WIN,   // debt 0
            HandOutcome.WIN,                    // access
        ).forEach { outcome ->
            val r = AccessPolicy.onHandResult(outcome, debt, t0)
            debt = r.debtMillis
            grantedUntil = r.grantedUntil
        }

        assertEquals(0, debt)
        assertEquals(t0 + win5, grantedUntil)
    }

    @Test
    fun `win with debt pays it down instead of granting`() {
        val r = AccessPolicy.onHandResult(HandOutcome.WIN, 20 * 60_000L, t0)
        assertEquals(10 * 60_000L, r.debtMillis)
        assertNull(r.grantedUntil)
    }

    @Test
    fun `debt is capped at sixty minutes`() {
        var debt = 55 * 60_000L
        repeat(3) {
            debt = AccessPolicy.onHandResult(HandOutcome.LOSE, debt, t0).debtMillis
        }
        assertEquals(60 * 60_000L, debt) // 55 + 10 → capped at 60, not 85
    }

    @Test
    fun `walk away with debt locks until debt served`() {
        val debt = 25 * 60_000L
        assertEquals(t0 + debt, AccessPolicy.lockoutUntil(debt, t0))
    }

    @Test
    fun `absence beyond sixty seconds revokes`() {
        val grant = GrantSnapshot("com.test", endAtMillis = t0 + win5, lastSeenMillis = t0 - 61_000L)
        assertTrue(AccessPolicy.shouldRevokeForAbsence(grant, t0))
    }

    @Test
    fun `absence within sixty seconds does not revoke`() {
        val grant = GrantSnapshot("com.test", endAtMillis = t0 + win5, lastSeenMillis = t0 - 59_000L)
        assertFalse(AccessPolicy.shouldRevokeForAbsence(grant, t0))
    }

    @Test
    fun `grant active only before end time`() {
        val grant = GrantSnapshot("com.test", endAtMillis = t0 + win5, lastSeenMillis = t0)
        assertTrue(AccessPolicy.isGrantActive(grant, t0 + win5 - 1))
        assertFalse(AccessPolicy.isGrantActive(grant, t0 + win5))
    }

    @Test
    fun `canPlay false while lockout active`() {
        val lockout = com.example.sonder.domain.model.LockoutSnapshot("com.test", t0 + 30_000L, 0)
        assertFalse(AccessPolicy.canPlay(lockout, t0))
        assertTrue(AccessPolicy.canPlay(lockout, t0 + 30_000L))
        assertTrue(AccessPolicy.canPlay(null, t0))
    }

    @Test
    fun `stateFor maps to canonical states`() {
        val grant = GrantSnapshot("p", t0 + win5, t0)
        val lockout = com.example.sonder.domain.model.LockoutSnapshot("p", t0 + 10_000L, 0)

        assertEquals(
            EnforcementState.DISABLED,
            AccessPolicy.stateFor("p", enabled = false, grant = grant, lockout = null, nowMillis = t0),
        )
        assertEquals(
            EnforcementState.GRANTED,
            AccessPolicy.stateFor("p", enabled = true, grant = grant, lockout = null, nowMillis = t0),
        )
        assertEquals(
            EnforcementState.LOCKED,
            AccessPolicy.stateFor("p", enabled = true, grant = null, lockout = lockout, nowMillis = t0),
        )
        assertEquals(
            EnforcementState.IDLE,
            AccessPolicy.stateFor("p", enabled = true, grant = null, lockout = null, nowMillis = t0),
        )
    }
}
