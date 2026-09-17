package com.example.sonder.ui

import androidx.navigation3.runtime.NavKey
import com.example.sonder.Main

/**
 * Pure Nav3 back-stack policy. Home is the root; at most one secondary destination
 * is kept, so tab switching can never pile up duplicate entries. No function mutates
 * its input list.
 */
object NavPolicy {

    /** Tab of the top destination; HOME when the top is Main or unrecognised. */
    fun tabOf(backStack: List<NavKey>): PixelTab {
        val top = backStack.lastOrNull() ?: return PixelTab.HOME
        return PixelTab.entries.firstOrNull { it.key == top } ?: PixelTab.HOME
    }

    /**
     * The stack that results from tapping [tab]: HOME clears every secondary
     * destination, a secondary tab replaces whatever secondary is on top, and
     * re-selecting the visible tab leaves the stack as it was.
     */
    fun select(backStack: List<NavKey>, tab: PixelTab): List<NavKey> = when {
        tab == PixelTab.HOME -> listOf(Main)
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
