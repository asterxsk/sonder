package com.example.sonder.ui.screens.home

import com.example.sonder.data.db.TargetEntity
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's pure mapper. Every case supplies `nowMillis`, so remaining text is
 * deterministic — no clock, no Room, no Android.
 */
class HomeStateTest {

    private val t0 = 1_000_000L

    private fun target(packageName: String, label: String, enabled: Boolean = true) = TargetEntity(
        packageName = packageName,
        label = label,
        enabled = enabled,
        createdAtMillis = 0L,
    )

    private fun grant(packageName: String, endAtMillis: Long) =
        GrantSnapshot(packageName = packageName, endAtMillis = endAtMillis, lastSeenMillis = t0)

    private fun lockout(packageName: String, untilMillis: Long) =
        LockoutSnapshot(packageName = packageName, untilMillis = untilMillis, debtMillis = 0L)

    private fun HomeState.row(packageName: String) = rows.single { it.packageName == packageName }

    @Test
    fun `excludes disabled targets`() {
        val state = mapHomeState(
            targets = listOf(target("com.a", "ALPHA"), target("com.b", "BETA", enabled = false)),
            grants = emptyList(),
            lockouts = emptyList(),
            nowMillis = t0,
        )

        assertEquals(listOf("com.a"), state.rows.map { it.packageName })
    }

    @Test
    fun `maps grants and lockouts to the right state at the supplied time`() {
        val targets = listOf(target("com.g", "GRANT"), target("com.l", "LOCK"), target("com.i", "IDLE"))
        val grants = listOf(grant("com.g", t0 + 60_000L))
        val lockouts = listOf(lockout("com.l", t0 + 120_000L))

        val now = mapHomeState(targets, grants, lockouts, nowMillis = t0)
        assertEquals(EnforcementState.GRANTED, now.row("com.g").state)
        assertEquals(EnforcementState.LOCKED, now.row("com.l").state)
        assertEquals(EnforcementState.IDLE, now.row("com.i").state)

        // The same records read at a later supplied time: both windows have closed.
        val later = mapHomeState(targets, grants, lockouts, nowMillis = t0 + 120_000L)
        assertEquals(EnforcementState.IDLE, later.row("com.g").state)
        assertEquals(EnforcementState.IDLE, later.row("com.l").state)
    }

    @Test
    fun `formats positive remaining time as MM SS`() {
        val state = mapHomeState(
            targets = listOf(target("com.g", "GRANT")),
            grants = listOf(grant("com.g", t0 + 125_000L)),
            lockouts = emptyList(),
            nowMillis = t0,
        )

        assertEquals("02:05", state.row("com.g").remainingText)
    }

    @Test
    fun `remaining text changes for timestamps one second apart`() {
        val targets = listOf(target("com.g", "GRANT"))
        val grants = listOf(grant("com.g", t0 + 60_000L))

        val first = mapHomeState(targets, grants, emptyList(), nowMillis = t0).row("com.g").remainingText
        val second = mapHomeState(targets, grants, emptyList(), nowMillis = t0 + 1_000L).row("com.g").remainingText

        assertEquals("01:00", first)
        assertEquals("00:59", second)
        assertNotEquals(first, second)
    }

    @Test
    fun `idle and expired rows carry no remaining text`() {
        val idle = mapHomeState(
            targets = listOf(target("com.i", "IDLE")),
            grants = emptyList(),
            lockouts = emptyList(),
            nowMillis = t0,
        )
        assertEquals("", idle.row("com.i").remainingText)

        val expired = mapHomeState(
            targets = listOf(target("com.g", "GRANT")),
            grants = listOf(grant("com.g", t0)),
            lockouts = emptyList(),
            nowMillis = t0,
        )
        assertEquals("", expired.row("com.g").remainingText)
    }

    @Test
    fun `orders locked then granted then idle, then by label`() {
        val state = mapHomeState(
            targets = listOf(
                target("com.i1", "BETA"),
                target("com.g1", "ZULU"),
                target("com.l1", "ALPHA"),
                target("com.l2", "CHARLIE"),
                target("com.i2", "ALPHA"),
            ),
            grants = listOf(grant("com.g1", t0 + 60_000L)),
            lockouts = listOf(lockout("com.l1", t0 + 60_000L), lockout("com.l2", t0 + 60_000L)),
            nowMillis = t0,
        )

        assertEquals(
            listOf("com.l1", "com.l2", "com.g1", "com.i2", "com.i1"),
            state.rows.map { it.packageName },
        )
    }

    @Test
    fun `summary picks the locked row over a live grant`() {
        val state = mapHomeState(
            targets = listOf(target("com.g", "GRANT APP"), target("com.l", "LOCK APP"), target("com.i", "IDLE APP")),
            grants = listOf(grant("com.g", t0 + 300_000L)),
            lockouts = listOf(lockout("com.l", t0 + 600_000L)),
            nowMillis = t0,
        )

        assertEquals(EnforcementState.LOCKED, state.row("com.l").state)
        assertEquals("10:00", state.row("com.l").remainingText)
        assertEquals(HomeSummary.Active(state.row("com.l")), state.summary)
    }

    @Test
    fun `summary picks a live grant over idle rows`() {
        val state = mapHomeState(
            targets = listOf(target("com.i", "IDLE APP"), target("com.g", "GRANT APP")),
            grants = listOf(grant("com.g", t0 + 300_000L)),
            lockouts = emptyList(),
            nowMillis = t0,
        )

        assertEquals(HomeSummary.Active(state.row("com.g")), state.summary)
    }

    @Test
    fun `no targets produces the no targets summary`() {
        val state = mapHomeState(
            targets = emptyList(),
            grants = emptyList(),
            lockouts = emptyList(),
            nowMillis = t0,
        )

        assertEquals(HomeSummary.NoTargets, state.summary)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `limited but inactive targets produce the idle summary with the enabled count`() {
        val state = mapHomeState(
            targets = listOf(
                target("com.a", "ALPHA"),
                target("com.b", "BETA"),
                target("com.c", "CHARLIE", enabled = false),
            ),
            grants = emptyList(),
            lockouts = emptyList(),
            nowMillis = t0,
        )

        assertEquals(HomeSummary.Idle(enabledCount = 2), state.summary)
    }

    @Test
    fun `expired grant and lockout fall back to the idle summary`() {
        val state = mapHomeState(
            targets = listOf(target("com.a", "ALPHA")),
            grants = listOf(grant("com.a", t0)),
            lockouts = listOf(lockout("com.a", t0)),
            nowMillis = t0,
        )

        assertEquals(HomeSummary.Idle(enabledCount = 1), state.summary)
    }

    @Test
    fun `disabling every target leaves nothing limited`() {
        val state = mapHomeState(
            targets = listOf(target("com.a", "ALPHA", enabled = false)),
            grants = emptyList(),
            lockouts = emptyList(),
            nowMillis = t0,
        )

        assertEquals(HomeSummary.NoTargets, state.summary)
        assertTrue(state.rows.isEmpty())
    }
}
