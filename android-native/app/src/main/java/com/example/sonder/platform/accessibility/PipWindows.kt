package com.example.sonder.platform.accessibility

/**
 * "Is this app playing on in a picture-in-picture window?"
 *
 * The one way out of the gate that does not go through it. A blocked app that is closed
 * while a video is playing can come back as a small floating window — YouTube does this on
 * the way out of Shorts — and a PiP window is not a window-state event for the app's main
 * activity, so nothing in the enforcement path names it. The app keeps playing above whatever
 * the user does next, having paid nothing for the privilege.
 *
 * Implemented by [SonderAccessibilityService] from the accessibility window list: a window
 * belonging to the app whose bounds are a small fraction of the screen. That is metadata
 * only — the same source [ForegroundWindows] and [ActiveWindowPackage] read — so no content
 * is inspected to answer it.
 *
 * It is a heuristic, and the failure direction is chosen: a window that cannot be measured
 * answers false, which leaves the app alone. Being wrong that way costs a video that keeps
 * playing; being wrong the other way pauses and covers an app the user is legitimately using
 * in a small window.
 */
fun interface PipWindows {

    /**
     * True only with positive evidence: [packageName] owns a window much smaller than the
     * screen.
     *
     * @param packageName the app to ask about, as the caller resolved it.
     */
    fun hasPipWindow(packageName: String): Boolean
}
