package com.example.sonder.domain

import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The gate decision table — what the blocker must do per foreground event.
 * Mirrors the flow in docs/android-native-v2.md §3.
 */
class GateDeciderTest {

    private val t0 = 1_000_000L
    private val pkg = "com.test.app"
    private val win5 = AccessPolicy.WIN_GRANT_MILLIS

    private fun grant(
        endAt: Long = t0 + win5,
        lastSeen: Long = t0,
    ) = GrantSnapshot(pkg, endAt, lastSeen)

    private fun lockout(until: Long = t0 + 30_000L) = LockoutSnapshot(pkg, until, 0L)

    @Test
    fun `non-target packages always pass`() {
        assertEquals(GateDecision.PASS, decide(targetEnabled = false, grant = grant(), lockout = null))
        assertEquals(GateDecision.PASS, decide(targetEnabled = false, grant = null, lockout = lockout()))
        assertEquals(GateDecision.PASS, decide(targetEnabled = false, grant = null, lockout = null))
    }

    @Test
    fun `active grant lets the app run`() {
        assertEquals(
            GateDecision.GRANTED,
            decide(targetEnabled = true, grant = grant(lastSeen = t0 - 5_000L), lockout = null),
        )
    }

    @Test
    fun `expired grant passes through to gating`() {
        // End time passed while the user was elsewhere: no revoke, but the app is gated again.
        assertEquals(
            GateDecision.GATE,
            decide(targetEnabled = true, grant = grant(endAt = t0 - 1), lockout = null),
        )
    }

    @Test
    fun `absence beyond the window revokes the grant`() {
        val stale = grant(lastSeen = t0 - AccessPolicy.ABSENCE_REVOKE_MILLIS - 1)
        assertEquals(GateDecision.REVOKE, decide(targetEnabled = true, grant = stale, lockout = null))
    }

    @Test
    fun `absence within the window keeps the grant`() {
        val fresh = grant(lastSeen = t0 - AccessPolicy.ABSENCE_REVOKE_MILLIS + 1_000L)
        assertEquals(GateDecision.GRANTED, decide(targetEnabled = true, grant = fresh, lockout = null))
    }

    @Test
    fun `absence boundary is exclusive at exactly sixty seconds`() {
        val exact = grant(lastSeen = t0 - AccessPolicy.ABSENCE_REVOKE_MILLIS)
        assertEquals(GateDecision.GRANTED, decide(targetEnabled = true, grant = exact, lockout = null))
    }

    @Test
    fun `active lockout shows the lockout blocker instead of the table`() {
        assertEquals(
            GateDecision.LOCKOUT,
            decide(targetEnabled = true, grant = null, lockout = lockout(until = t0 + 1)),
        )
    }

    @Test
    fun `expired lockout gates again`() {
        assertEquals(
            GateDecision.GATE,
            decide(targetEnabled = true, grant = null, lockout = lockout(until = t0)),
        )
    }

    @Test
    fun `lockout wins over a stale grant row`() {
        assertEquals(
            GateDecision.LOCKOUT,
            decide(targetEnabled = true, grant = grant(endAt = t0 - 1), lockout = lockout()),
        )
    }

    @Test
    fun `enabled target without grant or lockout gates`() {
        assertEquals(GateDecision.GATE, decide(targetEnabled = true, grant = null, lockout = null))
    }

    @Test
    fun `active grant beats an active lockout row`() {
        // Both rows present (stale DB rows) — an active grant should win.
        assertEquals(
            GateDecision.GRANTED,
            decide(targetEnabled = true, grant = grant(), lockout = lockout()),
        )
    }

    @Test
    fun `debt at its ceiling locks the app with no lockout row at all`() {
        // The ceiling is its own lock: the hand that reached it wrote a lockout, but the
        // wait must hold even if that row is gone, or the table reopens against a debt
        // that can no longer be paid down.
        assertEquals(
            GateDecision.LOCKOUT,
            decide(targetEnabled = true, grant = null, lockout = null, debtAtCap = true),
        )
    }

    @Test
    fun `debt below its ceiling still opens the table`() {
        assertEquals(
            GateDecision.GATE,
            decide(targetEnabled = true, grant = null, lockout = null, debtAtCap = false),
        )
    }

    @Test
    fun `a live grant still wins over a debt at the ceiling`() {
        // Precedence is unchanged: granted access is granted access.
        assertEquals(
            GateDecision.GRANTED,
            decide(targetEnabled = true, grant = grant(), lockout = null, debtAtCap = true),
        )
    }

    private fun decide(
        targetEnabled: Boolean,
        grant: GrantSnapshot?,
        lockout: LockoutSnapshot?,
        debtAtCap: Boolean = false,
    ): GateDecision = GateDecider.decide(
        targetEnabled = targetEnabled,
        grant = grant,
        lockout = lockout,
        nowMillis = t0,
        debtAtCap = debtAtCap,
    )
}
