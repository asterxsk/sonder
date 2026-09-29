package com.example.sonder.platform.accessibility

/**
 * "Is the window that just reported itself still the one in front?"
 *
 * Answered from the accessibility window list, which the service already receives for
 * `FLAG_RETRIEVE_INTERACTIVE_WINDOWS` and which needs no extra permission — only window
 * metadata, never content. Unlike a usage-stats lookup it cannot be stale: the list
 * describes the screen at the moment of the event, which is exactly what a release
 * decision needs, because a release is decided on a single event and a trailing launcher
 * event is indistinguishable from a real one by package name alone.
 *
 * Implemented by [SonderAccessibilityService]; nullable everywhere it is used, so an
 * environment where the list is unavailable simply falls back to the other evidence.
 */
fun interface ForegroundWindows {

    /**
     * True only with positive evidence that [windowId] is no longer what the user is
     * looking at: some other window holds input focus *and* [windowId] is still listed.
     *
     * A window that has left the list is gone rather than behind, which is a real
     * departure and must release; and no focused window at all means the list cannot
     * answer the question, which must not hold the blocker up on a guess.
     */
    fun isBehindAnotherWindow(windowId: Int): Boolean
}
