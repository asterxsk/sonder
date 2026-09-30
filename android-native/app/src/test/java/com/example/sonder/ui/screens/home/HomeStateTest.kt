package com.example.sonder.ui.screens.home

import com.example.sonder.data.db.TargetEntity
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.TimeBankSnapshot
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home's mapper. It reads a bank that belongs to a day, so the day is an input here as much as
 * the clock is — a row that reads yesterday's balance as today's is the whole failure mode this
 * mapper has to not have.
 */
class HomeStateTest {

    private val utc = ZoneId.of("UTC")
    private val t0 = 1_700_000_000_000L
    private val today = AccessPolicy.epochDayOf(t0, utc)

    @Test
    fun `no targets is the LIMIT APPS prompt`() {
        val state = mapHomeState(targets = emptyList(), banks = emptyList(), nowMillis = t0, zoneId = utc)
        assertEquals(HomeSummary.NoTargets, state.summary)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `a disabled target is not on Home at all`() {
        val state = mapHomeState(
            targets = listOf(target("com.example.a", enabled = false)),
            banks = listOf(bank("com.example.a", 30 * 60_000L)),
            nowMillis = t0,
            zoneId = utc,
        )
        assertEquals(HomeSummary.NoTargets, state.summary)
        assertTrue(state.rows.isEmpty())
    }

    @Test
    fun `an enabled target with nothing banked is idle`() {
        val state = mapHomeState(
            targets = listOf(target("com.example.a")),
            banks = emptyList(),
            nowMillis = t0,
            zoneId = utc,
        )
        assertEquals(HomeSummary.Idle(enabledCount = 1), state.summary)
        assertEquals(0L, state.rows.single().bankMillis)
        assertEquals("", state.rows.single().remainingText)
    }

    @Test
    fun `an enabled target with a bank is the active row`() {
        val state = mapHomeState(
            targets = listOf(target("com.example.a", label = "App")),
            banks = listOf(bank("com.example.a", 29 * 60_000L)),
            nowMillis = t0,
            zoneId = utc,
        )
        val row = (state.summary as HomeSummary.Active).row
        assertEquals("com.example.a", row.packageName)
        assertEquals(29 * 60_000L, row.bankMillis)
        assertEquals("29:00", row.remainingText)
        assertEquals(EnforcementState.GRANTED, row.state)
    }

    @Test
    fun `a bank from yesterday reads as nothing and the row goes idle`() {
        // The daily allowance without a daily table: the row survives midnight, it simply
        // stops counting, and every app starts the day gated.
        val state = mapHomeState(
            targets = listOf(target("com.example.a")),
            banks = listOf(bank("com.example.a", 30 * 60_000L, epochDay = today - 1)),
            nowMillis = t0,
            zoneId = utc,
        )
        assertEquals(HomeSummary.Idle(enabledCount = 1), state.summary)
        assertEquals(0L, state.rows.single().bankMillis)
        assertEquals(EnforcementState.IDLE, state.rows.single().state)
    }

    @Test
    fun `the panel names the bank that is about to run out`() {
        val state = mapHomeState(
            targets = listOf(target("com.example.a"), target("com.example.b")),
            banks = listOf(
                bank("com.example.a", 30 * 60_000L),
                bank("com.example.b", 2 * 60_000L),
            ),
            nowMillis = t0,
            zoneId = utc,
        )
        assertEquals("com.example.b", (state.summary as HomeSummary.Active).row.packageName)
        // ...and the list agrees: the smaller bank is the one that matters first.
        assertEquals("com.example.b", state.rows.first().packageName)
    }

    @Test
    fun `rows with no bank fall back to name order`() {
        val state = mapHomeState(
            targets = listOf(target("com.example.c"), target("com.example.a")),
            banks = emptyList(),
            nowMillis = t0,
            zoneId = utc,
        )
        assertEquals(listOf("com.example.a", "com.example.c"), state.rows.map { it.packageName })
    }

    @Test
    fun `a granted row outranks an idle one`() {
        val state = mapHomeState(
            targets = listOf(target("com.example.a"), target("com.example.z")),
            banks = listOf(bank("com.example.z", 60_000L)),
            nowMillis = t0,
            zoneId = utc,
        )
        assertEquals("com.example.z", state.rows.first().packageName)
    }

    @Test
    fun `a bank with nothing left is not an active row`() {
        val state = mapHomeState(
            targets = listOf(target("com.example.a")),
            banks = listOf(bank("com.example.a", 0L, emptySinceMillis = t0)),
            nowMillis = t0,
            zoneId = utc,
        )
        assertEquals(HomeSummary.Idle(enabledCount = 1), state.summary)
        assertNull(state.rows.single().badgeText())
    }

    @Test
    fun `the idle count is the number of limited apps`() {
        val state = mapHomeState(
            targets = listOf(target("com.example.a"), target("com.example.b"), target("com.example.c")),
            banks = emptyList(),
            nowMillis = t0,
            zoneId = utc,
        )
        assertEquals(HomeSummary.Idle(enabledCount = 3), state.summary)
    }

    @Test
    fun `a row with time reads GRANTED and one without reads LOCKED`() {
        val granted = row(bankMillis = 30 * 60_000L)
        assertEquals(HomeBadge.GRANTED, granted.badge())
        assertEquals("30:00", granted.badgeText())
        assertEquals("ACCESS GRANTED", granted.stateWord())

        val idle = row(bankMillis = 0L)
        assertEquals(HomeBadge.LOCKED, idle.badge())
        assertEquals("LOCKED", idle.stateWord())
    }

    @Test
    fun `remaining text is minutes and seconds`() {
        assertEquals("00:30", formatRemaining(30_000L))
        assertEquals("29:30", formatRemaining(29 * 60_000L + 30_000L))
    }

    private fun target(
        packageName: String,
        label: String = "App",
        enabled: Boolean = true,
    ) = TargetEntity(
        packageName = packageName,
        label = label,
        enabled = enabled,
        createdAtMillis = t0,
    )

    private fun bank(
        packageName: String,
        remainingMillis: Long,
        epochDay: Long = today,
        emptySinceMillis: Long = 0L,
    ) = TimeBankSnapshot(
        packageName = packageName,
        remainingMillis = remainingMillis,
        epochDay = epochDay,
        lastSeenMillis = t0,
        emptySinceMillis = emptySinceMillis,
    )

    private fun row(bankMillis: Long) = HomeRow(
        packageName = "com.example.a",
        label = "App",
        state = AccessPolicy.stateFor(enabled = true, bankMillis = bankMillis),
        bankMillis = bankMillis,
        remainingText = if (bankMillis > 0L) formatRemaining(bankMillis) else "",
    )
}
