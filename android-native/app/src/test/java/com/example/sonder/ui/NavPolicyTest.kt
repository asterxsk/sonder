package com.example.sonder.ui

import androidx.navigation3.runtime.NavKey
import com.example.sonder.Main
import com.example.sonder.Settings
import com.example.sonder.Stats
import com.example.sonder.Targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** NavPolicy: Home is the root and at most one secondary tab is on the stack. */
class NavPolicyTest {

    @Test
    fun `home clears a secondary destination`() {
        assertEquals(listOf(Main), NavPolicy.select(listOf(Main, Stats), PixelTab.HOME))
    }

    @Test
    fun `home from home keeps only the root`() {
        assertEquals(listOf(Main), NavPolicy.select(listOf(Main), PixelTab.HOME))
    }

    @Test
    fun `a secondary tab with only home on the stack adds it`() {
        assertEquals(listOf(Main, Targets), NavPolicy.select(listOf(Main), PixelTab.TARGETS))
    }

    @Test
    fun `a secondary tab replaces another secondary tab`() {
        val result = NavPolicy.select(listOf(Main, Stats), PixelTab.TARGETS)

        assertEquals(listOf(Main, Targets), result)
        assertEquals(2, result.size) // one copy of the destination, never appended
    }

    @Test
    fun `selecting the visible tab does not duplicate it`() {
        val stack: List<NavKey> = listOf(Main, Targets)
        val result = NavPolicy.select(stack, PixelTab.TARGETS)

        assertSame(stack, result)
        assertEquals(2, result.size)
    }

    @Test
    fun `back from a secondary destination reveals home`() {
        assertEquals(listOf(Main), NavPolicy.back(listOf(Main, Settings)))
    }

    @Test
    fun `back at the root leaves the stack unchanged`() {
        val stack: List<NavKey> = listOf(Main)

        assertSame(stack, NavPolicy.back(stack))
    }

    @Test
    fun `tabOf reads the top destination and defaults to home`() {
        assertEquals(PixelTab.TARGETS, NavPolicy.tabOf(listOf(Main, Targets)))
        assertEquals(PixelTab.HOME, NavPolicy.tabOf(listOf(Main)))
        assertEquals(PixelTab.HOME, NavPolicy.tabOf(emptyList()))
    }

    @Test
    fun `every tab round-trips through its key`() {
        PixelTab.entries.forEach { tab ->
            assertEquals(tab, NavPolicy.tabOf(listOf(Main, tab.key)))
        }
    }

    @Test
    fun `select and back never mutate the input stack`() {
        val stack = mutableListOf<NavKey>(Main, Stats)

        NavPolicy.select(stack, PixelTab.TARGETS)
        NavPolicy.back(stack)

        assertEquals(listOf<NavKey>(Main, Stats), stack)
    }
}
