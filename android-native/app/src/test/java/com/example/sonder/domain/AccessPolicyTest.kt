package com.example.sonder.domain

import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.HandOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

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
        val lockout = com.example.sonder.domain.model.LockoutSnapshot("com.test", t0 + 30_000L)
        assertFalse(AccessPolicy.canPlay(lockout, t0))
        assertTrue(AccessPolicy.canPlay(lockout, t0 + 30_000L))
        assertTrue(AccessPolicy.canPlay(null, t0))
    }

    @Test
    fun `stateFor maps to canonical states`() {
        val grant = GrantSnapshot("p", t0 + win5, t0)
        val lockout = com.example.sonder.domain.model.LockoutSnapshot("p", t0 + 10_000L)

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

    // --- per-app rules and the daily cap ---

    private val utc = ZoneId.of("UTC")

    @Test
    fun `null cap is identical to the global policy`() {
        val plain = AccessPolicy.onHandResult(HandOutcome.WIN, 0, t0)
        val ruled = AccessPolicy.onHandResult(HandOutcome.WIN, 0, t0, AccessRules(), 0L, utc)

        assertEquals(plain, ruled)
        assertNull(ruled.capLockoutUntil)
    }

    @Test
    fun `per-app grant and debt sizes override the defaults`() {
        val rules = AccessRules(winGrantMillis = 2 * 60_000L, lossDebtMillis = 3 * 60_000L, maxDebtMillis = 4 * 60_000L)

        val win = AccessPolicy.onHandResult(HandOutcome.WIN, 0, t0, rules)
        assertEquals(t0 + 2 * 60_000L, win.grantedUntil)

        val loss = AccessPolicy.onHandResult(HandOutcome.LOSE, 0, t0, rules)
        assertEquals(3 * 60_000L, loss.debtMillis)

        val capped = AccessPolicy.onHandResult(HandOutcome.LOSE, 4 * 60_000L, t0, rules)
        assertEquals(4 * 60_000L, capped.debtMillis) // 4 + 3 clamped to the per-app max
    }

    @Test
    fun `cap allows a full win grant while there is room`() {
        val rules = AccessRules(dailyCapMillis = 10 * 60_000L)
        val r = AccessPolicy.onHandResult(HandOutcome.WIN, 0, t0, rules, grantedTodayMillis = 0L, zoneId = utc)

        assertEquals(0, r.debtMillis)
        assertEquals(t0 + win5, r.grantedUntil)
        assertNull(r.capLockoutUntil)
    }

    @Test
    fun `cap clamps the grant to the remaining allowance`() {
        val remaining = 3 * 60_000L
        val rules = AccessRules(dailyCapMillis = 10 * 60_000L)
        val r = AccessPolicy.onHandResult(
            HandOutcome.WIN, 0, t0, rules,
            grantedTodayMillis = 10 * 60_000L - remaining, zoneId = utc,
        )

        assertEquals(t0 + remaining, r.grantedUntil) // min(5:00, 3:00)
        assertEquals(86_400_000L, r.capLockoutUntil) // 1970-01-01 00:16:40Z → next UTC midnight
    }

    @Test
    fun `cap spent grants nothing and locks until the next local midnight`() {
        val rules = AccessRules(dailyCapMillis = 10 * 60_000L)
        val r = AccessPolicy.onHandResult(
            HandOutcome.WIN, 0, t0, rules,
            grantedTodayMillis = 10 * 60_000L, zoneId = utc,
        )

        assertNull(r.grantedUntil)
        assertEquals(86_400_000L, r.capLockoutUntil)
        assertEquals(0, r.debtMillis)
    }

    @Test
    fun `a win that clears debt bypasses the cap`() {
        val rules = AccessRules(dailyCapMillis = 1 * 60_000L)
        val r = AccessPolicy.onHandResult(
            HandOutcome.WIN, 20 * 60_000L, t0, rules,
            grantedTodayMillis = 10 * 60_000L, zoneId = utc,
        )

        assertEquals(10 * 60_000L, r.debtMillis) // 20 − 10, debt paid, no grant, no cap lockout
        assertNull(r.grantedUntil)
        assertNull(r.capLockoutUntil)
    }

    @Test
    fun `nextLocalMidnight rolls to the following day`() {
        assertEquals(86_400_000L, AccessPolicy.nextLocalMidnight(t0, utc))
        assertEquals(2 * 86_400_000L, AccessPolicy.nextLocalMidnight(86_400_000L, utc))
    }

    // --- the debt ceiling stops the game ---

    @Test
    fun `debt below the ceiling keeps the table open`() {
        // Winning hands are the fast way to pay debt down, so the loop stays open until
        // there is nothing left that another loss could change.
        assertFalse(AccessPolicy.isDebtAtCap(59 * 60_000L))
    }

    @Test
    fun `the ceiling itself stops the game`() {
        assertTrue(AccessPolicy.isDebtAtCap(60 * 60_000L))
    }

    @Test
    fun `debt above the ceiling stays locked`() {
        assertTrue(AccessPolicy.isDebtAtCap(75 * 60_000L))
    }

    @Test
    fun `the ceiling follows a per-app override`() {
        assertTrue(AccessPolicy.isDebtAtCap(30 * 60_000L, maxDebtMillis = 30 * 60_000L))
        assertFalse(AccessPolicy.isDebtAtCap(30 * 60_000L, maxDebtMillis = 45 * 60_000L))
    }

    @Test
    fun `paying debt down shortens the lockout deadline`() {
        // The lockout row is a deadline written when the debt was larger. A win that pays
        // debt down has to rewrite it, or the wait outlives the debt it was serving.
        val before = AccessPolicy.lockoutUntil(20 * 60_000L, t0)
        val paid = AccessPolicy.onHandResult(HandOutcome.WIN, 20 * 60_000L, t0, AccessRules()).debtMillis
        val after = AccessPolicy.lockoutUntil(paid, t0)

        assertEquals(10 * 60_000L, paid)
        assertEquals(t0 + 10 * 60_000L, after)
        assertTrue(after < before)
    }

    // --- an override is user input, and the clamp is what makes it safe ---

    @Test
    fun `a zero debt ceiling is pulled up to the shortest usable span`() {
        // At 0, isDebtAtCap is true at all times: the target locks out for good and the
        // table never opens again, so the clamp is the difference between a bad setting and
        // a bricked app.
        val rules = AccessRules(maxDebtMillis = 0L).clamped()

        assertEquals(10_000L, rules.maxDebtMillis)
        assertFalse(AccessPolicy.isDebtAtCap(9_000L, rules.maxDebtMillis))
    }

    @Test
    fun `a negative loss cannot refund debt`() {
        val rules = AccessRules(lossDebtMillis = -60_000L).clamped()

        assertEquals(10_000L, rules.lossDebtMillis)
        assertEquals(10_000L, AccessPolicy.onHandResult(HandOutcome.LOSE, 0L, t0, rules).debtMillis)
    }

    @Test
    fun `a zero daily cap becomes the shortest real cap rather than a spent one`() {
        // A stored 0 spends the whole allowance before the first hand, so no win could ever
        // grant access again.
        assertEquals(10_000L, AccessRules(dailyCapMillis = 0L).clamped().dailyCapMillis)
    }

    @Test
    fun `no cap stays no cap`() {
        assertNull(AccessRules(dailyCapMillis = null).clamped().dailyCapMillis)
    }

    @Test
    fun `an absurd override is pulled down to a day`() {
        val rules = AccessRules(
            winGrantMillis = Long.MAX_VALUE,
            lossDebtMillis = 10 * 86_400_000L,
            maxDebtMillis = Long.MAX_VALUE,
            absenceRevokeMillis = Long.MAX_VALUE,
        ).clamped()

        assertEquals(86_400_000L, rules.winGrantMillis)
        assertEquals(86_400_000L, rules.lossDebtMillis)
        assertEquals(86_400_000L, rules.maxDebtMillis)
        assertEquals(86_400_000L, rules.absenceRevokeMillis)
    }
}
