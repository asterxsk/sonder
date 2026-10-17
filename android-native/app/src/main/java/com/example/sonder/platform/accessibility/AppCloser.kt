package com.example.sonder.platform.accessibility

/**
 * "Get the user out of this app."
 *
 * The blocker is an opaque overlay, so a gated app is visible to the user only as a wall
 * they have to leave by pressing Back or Home themselves — and Back is not reliably an exit
 * from an app that handles it internally, so the wall can feel like a dead end. This is the
 * capability behind the gate's CLOSE control: an explicit way out that the blocker can
 * offer, rather than a hint about which system gesture to use.
 *
 * Implemented by [SonderAccessibilityService] and handed to the coordinator the same way
 * [ForegroundWindows] is. Nullable everywhere it is used: without a connected service there
 * is nothing to ask, and the CLOSE control simply has no effect beyond the blocker going
 * away.
 */
fun interface AppCloser {

    /**
     * Leave [packageName]: send the user Home, and ask the platform to drop the app's
     * background process.
     *
     * **The two halves do not carry equal weight, and the KDoc says so because the caller's
     * UI text must not overpromise.** `GLOBAL_ACTION_HOME` is what actually closes the
     * experience — the app leaves the screen and the blocker with it. The process kill is a
     * best-effort nudge: `killBackgroundProcesses` cannot touch an app that is in the
     * foreground on any modern Android release, and it cannot touch one holding a
     * picture-in-picture window either, because a pinned task is visible and therefore carries
     * visible importance. So on the devices where a float exists the kill does nothing at all,
     * and the Home action is not merely incomplete there — it is what *creates* the float, by
     * giving a playing app the task switch that hands it the right to enter PiP.
     *
     * The conclusion that follows is about the *player's state*, not about the order of
     * operations inside this method, and it is worth stating precisely because the obvious fix
     * is the wrong one. What must be true before Home is sent is that the app is not playing —
     * that is what stops Android handing it a picture-in-picture window on the way out. What
     * must *not* be assumed is that this method can establish it. The pause available here is a
     * toggle, so "pause before leaving" is only safe when the player is known to be running,
     * and by the time the user taps CLOSE the gate has usually stopped it already; a second
     * toggle then starts it, and the app reaches Home playing — the float this class exists to
     * prevent. The gate therefore pauses once, on the way *in*, where it knows the player was
     * playing because it just saw the surface. This method leaves.
     *
     * The user's next launch of the app starts where the app is, not where the blocker left
     * it: no granted time is created or extended by closing, and the target is still gated
     * on the way back in.
     */
    fun closePackage(packageName: String)
}
