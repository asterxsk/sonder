package com.example.sonder.platform.accessibility

/**
 * "Does the window on screen right now carry any of these view identifiers?"
 *
 * This is the one place the blocker looks at anything other than a package name, and it is
 * built to look at as little as possible. It reports a yes/no over a caller-supplied list
 * of *identifier fragments*, never the identifiers themselves, never a class name, never
 * any text: the caller already knows what it is asking about, so the answer cannot be used
 * to learn anything the caller did not already say. Nothing is stored, logged or sent.
 *
 * It exists because a short-form video surface is not a window. Reels and Shorts run inside
 * the app's own main activity, so no window-state event ever names them and the foreground
 * package is identical to the feed's — the view identifiers are the only signal that
 * separates the two, and without them a whole-app gate is the only gate possible.
 *
 * Implemented by [SonderAccessibilityService]; nullable everywhere it is used, so a
 * configuration where the node tree cannot be read simply falls back to "not the scoped
 * surface" and the app passes rather than being wrongly gated.
 */
fun interface ActiveWindowContent {

    /**
     * True only with positive evidence: one of [markers] was found in the active window's
     * node tree.
     *
     * A window that cannot be read, a tree that is still being built, a disconnected service
     * — every one of those answers false, and false means "this is not the surface the user
     * asked to gate", which lets the app through. That direction is chosen deliberately: the
     * alternative is raising a blocker over an app on a guess, and being wrong that way is
     * what makes a blocker feel broken rather than strict.
     *
     * @param packageName the app the markers belong to, as the caller resolved it.
     * @param markers identifier fragments to look for, matched case-insensitively as
     *   substrings — see [com.example.sonder.domain.ShortsCatalog], which owns the list.
     */
    fun showsMarkers(packageName: String, markers: List<String>): Boolean
}
