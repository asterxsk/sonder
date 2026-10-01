package com.example.sonder.ui

import androidx.navigation3.runtime.NavKey
import com.example.sonder.AppSettings
import com.example.sonder.Main
import com.example.sonder.TargetPicker

/**
 * Pure Nav3 back-stack policy. Home is the root; switching tabs replaces the single
 * secondary destination, so tab switching can never pile up duplicate entries. A
 * TARGETS detail — an app's settings, or the add picker — rides on top of the TARGETS
 * tab, never on its own, so Back and the dock both have a tab to fall back to, and the
 * stack is never emptied. No function mutates its input list.
 */
object NavPolicy {

    /** Tab of the top destination; HOME when the top is Main or unrecognised. */
    fun tabOf(backStack: List<NavKey>): PixelTab {
        val top = backStack.lastOrNull() ?: return PixelTab.HOME
        // A Targets detail belongs to the Targets flow, so the dock keeps TARGETS lit.
        if (top.isTargetsDetail()) return PixelTab.TARGETS
        return PixelTab.entries.firstOrNull { it.key == top } ?: PixelTab.HOME
    }

    /**
     * The stack that results from tapping [tab]: HOME clears every secondary
     * destination, a secondary tab replaces whatever secondary is on top, and
     * re-selecting the tab already on top leaves the stack as it was.
     */
    fun select(backStack: List<NavKey>, tab: PixelTab): List<NavKey> = when {
        tab == PixelTab.HOME -> listOf(Main)
        // A Targets detail is not a tab, so any dock tap replaces it with that tab:
        // re-selecting TARGETS there must still leave the detail for the Targets list.
        backStack.lastOrNull().isTargetsDetail() -> listOf(Main, tab.key)
        tabOf(backStack) == tab -> backStack
        else -> listOf(Main, tab.key)
    }

    /**
     * The stack that results from pushing a detail destination.
     *
     * A push onto a destination already on top is a no-op. Without this a double tap on a
     * row's arrow, or on ADD, appends the same key twice — two `AppSettings` or two
     * `TargetPicker` entries, where the second is invisible and the first CANCEL only
     * reveals it. That is a screen the user has to dismiss twice, and it is what
     * `leavePicker` in the host was manually papering over by checking the top before
     * popping. The check belongs here, where the stack shape is decided.
     *
     * Only the *top* is compared, so the stack stays a path rather than a set: pressing
     * deeper into the same destination from a different one is still a push.
     */
    fun push(backStack: List<NavKey>, key: NavKey): List<NavKey> =
        if (backStack.lastOrNull() == key) backStack else backStack + key

    /** Destinations that belong to the TARGETS flow but are not the tab itself. */
    private fun NavKey?.isTargetsDetail(): Boolean = this is AppSettings || this is TargetPicker

    /**
     * The stack that results from Android Back: a secondary destination on top is
     * dropped, revealing Main. At the root the stack is unchanged — finishing the
     * activity is the caller's job, not this policy's.
     */
    fun back(backStack: List<NavKey>): List<NavKey> =
        if (backStack.size > 1) backStack.dropLast(1) else backStack

    /**
     * Back as the host has to apply it: null means "nowhere left to go — leave the app".
     * [back] cannot express that, because at the root it returns the stack unchanged, and a
     * caller that applies an unchanged stack has swallowed the press: Back on Home did
     * nothing at all. The decision lives here rather than in the caller so it is testable.
     */
    fun backOrLeave(backStack: List<NavKey>): List<NavKey>? =
        if (backStack.size > 1) backStack.dropLast(1) else null
}
