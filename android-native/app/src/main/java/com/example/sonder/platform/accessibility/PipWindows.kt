package com.example.sonder.platform.accessibility

import com.example.sonder.domain.FloatingHold

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
 * The answer is [FloatingHold.Answer], not a boolean, because the probe has three outcomes and
 * the caller must not conflate two of them. An unreadable window list is [Answer.Unknown] and
 * *not* [Answer.None]: the probe is starved exactly when the service's own thread is busy, and
 * a busy service's thread is what a playing Short produces, so the moment this answer matters
 * most is the moment a blind read is most likely.
 *
 * **The batch is the interface, not a convenience on top of it.** Every caller in the
 * enforcement path is asking about the whole enabled-target list at once, and the window list
 * and its roots are read from the service's own thread — one post-and-await, bounded by a
 * timeout, per call. Answering one package at a time would pay that bound once per target on
 * every 500 ms pass; answering the set pays it once. [pipWindowFor] is the single-question form
 * for a caller that has exactly one, and it is defined in terms of the batch so the two can
 * never disagree.
 *
 * **Carrying the distinction is the caller's job, and one of them drops it.** [Answer] is
 * a value it can report, not a guarantee it can enforce: a caller that keeps it — the
 * coordinator's own pass, which folds it into [FloatingHold] — gets the "hold what you have"
 * behaviour, while a caller that reduces the answer to a package name has already turned
 * [Answer.Unknown] into "nothing found" before [FloatingHold.releaseAllowed] ever sees it. That
 * reduction is safe only because the hold is consulted alongside it and refuses a standing
 * one, which is why every such call site asks about the hold in the same breath. Any new
 * caller that wants the distinction has to take [Answer] itself.
 */
fun interface PipWindows {

    /**
     * What each of [packageNames] is doing in the window list right now.
     *
     * @param packageNames the apps to ask about, as the caller resolved them.
     * @return one entry per requested package, so a caller never has to tell "not asked" from
     *   "not floating". [FloatingHold.Answer.Found] only with positive evidence,
     *   [FloatingHold.Answer.Unknown] when the list could not be read for any reason.
     */
    fun pipWindowsAmong(packageNames: List<String>): Map<String, FloatingHold.Answer>

    /**
     * What [packageName] is doing in the window list right now.
     *
     * A missing entry — impossible for a well-behaved implementation, given the contract above
     * — reads as [FloatingHold.Answer.Unknown] rather than as absence, because "nothing there"
     * is the one answer that must never be inferred.
     */
    fun pipWindowFor(packageName: String): FloatingHold.Answer =
        pipWindowsAmong(listOf(packageName))[packageName] ?: FloatingHold.Answer.Unknown

    companion object {

        /**
         * The answer for each of [packageNames], from the packages the small windows resolved to.
         *
         * Split out of the service because it is the whole of the rule and none of the platform
         * work: the caller has already measured the windows and read their roots, and what is
         * left is deciding what those reads *mean*. [roots] is one entry per small candidate,
         * with null for a window whose root would not resolve — a value the service produces on
         * the device and a test can produce anywhere.
         *
         * **A positive answer beats an unreadable one, and that ordering is the point.** A
         * package whose own window was read is [FloatingHold.Answer.Found] whatever else went
         * unreadable; only a package that was *not* found falls back to
         * [FloatingHold.Answer.Unknown], because an unresolved window could have been its own.
         * Letting blindness win instead would report a genuinely floating target as unreadable
         * — and "nothing found, nothing readable" is precisely the pair that frees a caller to
         * release the blocker over the float it should have been covering.
         */
        fun resolve(
            packageNames: List<String>,
            roots: List<String?>,
        ): Map<String, FloatingHold.Answer> {
            val found = roots.filterNotNull().toHashSet()
            // Asked of the roots directly rather than of the two counts: two candidates naming
            // the same package are a smaller set than they are a list, and comparing sizes
            // would read a duplicate as an unresolved window.
            val blind = roots.any { it == null }
            return packageNames.associateWith { pkg ->
                when {
                    pkg in found -> FloatingHold.Answer.Found(pkg)
                    blind -> FloatingHold.Answer.Unknown
                    else -> FloatingHold.Answer.None
                }
            }
        }
    }
}
