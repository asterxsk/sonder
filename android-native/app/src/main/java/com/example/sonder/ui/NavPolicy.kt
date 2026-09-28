package com.example.sonder.ui

import androidx.navigation3.runtime.NavKey
import com.example.sonder.AppSettings
import com.example.sonder.Main

/**
 * Pure Nav3 back-stack policy. Home is the root; switching tabs replaces the single
 * secondary destination, so tab switching can never pile up duplicate entries. An
 * AppSettings detail rides on top of the TARGETS tab — never on its own — so Back and
 * the dock both have a tab to fall back to, and the stack is never emptied. No function
 * mutates its input list.
 */
object NavPolicy {

    /** Tab of the top destination; HOME when the top is Main or unrecognised. */
    fun tabOf(backStack: List<NavKey>): PixelTab {
        val top = backStack.lastOrNull() ?: return PixelTab.HOME
        // An app's settings detail belongs to the Targets flow, so the dock keeps TARGETS lit.
        if (top is AppSettings) return PixelTab.TARGETS
        return PixelTab.entries.firstOrNull { it.key == top } ?: PixelTab.HOME
    }

    /**
     * The stack that results from tapping [tab]: HOME clears every secondary
     * destination, a secondary tab replaces whatever secondary is on top, and
     * re-selecting the tab already on top leaves the stack as it was.
     */
    fun select(backStack: List<NavKey>, tab: PixelTab): List<NavKey> = when {
        tab == PixelTab.HOME -> listOf(Main)
        // An AppSettings detail is not a tab, so any dock tap replaces it with that tab:
        // re-selecting TARGETS there must still leave the detail for the Targets list.
        backStack.lastOrNull() is AppSettings -> listOf(Main, tab.key)
        tabOf(backStack) == tab -> backStack
        else -> listOf(Main, tab.key)
    }

    /**
     * The stack that results from Android Back: a secondary destination on top is
     * dropped, revealing Main. At the root the stack is unchanged — finishing the
     * activity is the caller's job, not this policy's.
     */
    fun back(backStack: List<NavKey>): List<NavKey> =
        if (backStack.size > 1) backStack.dropLast(1) else backStack
}
