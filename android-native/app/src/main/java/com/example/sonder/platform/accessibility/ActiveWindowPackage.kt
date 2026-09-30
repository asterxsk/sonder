package com.example.sonder.platform.accessibility

/**
 * "Which app is on screen right now?" — answered from the accessibility window list.
 *
 * This is the immediate answer that [com.example.sonder.platform.foreground.ForegroundResolver]
 * cannot give. That resolver reads `UsageStatsManager.queryEvents`, which the platform
 * batches: a resume event can take seconds to become visible, so for a moment after an app
 * comes up the lookup still names whatever was in front before it. Raising is the safe
 * direction and survives that lag; *releasing* does not, and the foreground re-check is the
 * path that releases on it.
 *
 * The window list has no such lag — it describes the screen at the moment it is read — and
 * the service already receives it for `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`. Only window
 * metadata is read, never content.
 *
 * Null means "cannot answer", which callers must treat as no corroboration *against* a
 * release rather than as evidence for one: an environment where the list is unavailable
 * keeps the behaviour it had before this existed.
 *
 * Implemented by [SonderAccessibilityService].
 */
fun interface ActiveWindowPackage {

    /**
     * The package of the window holding input focus, or null when that cannot be
     * determined — no focused window, a window whose root cannot be read, or a window
     * that is not an app at all.
     *
     * Our own blocker is deliberately not focusable, so while it covers a blocked app
     * the app's window is still the one with focus, and this answers "which app is the
     * user in" rather than "what is drawn on top".
     */
    fun activePackage(): String?
}
