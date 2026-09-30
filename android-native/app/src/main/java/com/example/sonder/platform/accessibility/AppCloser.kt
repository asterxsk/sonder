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
     * foreground on any modern Android release, and by the time this is called the app has
     * only just been sent to the background, so on most devices it is a no-op. It is kept
     * because it does help in the case it can — an app the user has already left, still
     * holding a warm process and a paused activity — and it costs nothing when it does not.
     *
     * The user's next launch of the app starts where the app is, not where the blocker left
     * it: no granted time is created or extended by closing, and the target is still gated
     * on the way back in.
     */
    fun closePackage(packageName: String)
}
