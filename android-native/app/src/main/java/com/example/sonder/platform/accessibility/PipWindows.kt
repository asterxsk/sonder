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
 * **Carrying the distinction is now the caller's job, and one of them drops it.** [Answer] is
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
     * What [packageName] is doing in the window list right now.
     *
     * @param packageName the app to ask about, as the caller resolved it.
     * @return [FloatingHold.Answer.Found] only with positive evidence, [FloatingHold.Answer.Unknown]
     *   when the list could not be read for any reason.
     */
    fun pipWindowFor(packageName: String): FloatingHold.Answer
}
