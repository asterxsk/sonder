package com.example.sonder.domain

/**
 * How long the blocker holds on to a picture-in-picture window it was raised over.
 *
 * A gated app with an empty bank that is playing in a float is the one case the gate cannot
 * settle by itself: the float is not a window-state event for the app's activity, so the
 * event stream names nothing, and the two probes that could name it disagree across the
 * moment the app changes windows — the window list is read while the transition is still
 * happening, and the usage-event lookup the release path decides on is batched by the
 * platform. One pass covers the float, the next sees no float and releases, and the user
 * watches the blocker strobe over the video they are still watching.
 *
 * So a release is counted rather than accepted: [CONFIRMING_PASSES] passes in a row must
 * find no float before the hold ends. The asymmetry matches [ForegroundWatch]'s — releasing
 * wrongly exposes an app the user is watching, while holding a pass or two too long only
 * delays the correction, and the counter reaches its threshold on its own with no further
 * evidence, so a hold can never become permanent.
 *
 * The hold is one immutable value rather than a package and a counter side by side. It is
 * read and written by the coordinator's dispatcher and cleared by the accessibility
 * service's own thread when the screen goes off or the service unbinds, and two fields
 * cannot be moved between those threads as one fact: a reader that saw the new package with
 * the old run of misses — or a clear that landed between the two writes — would end the
 * next hold early or keep a dropped one alive. One field swaps atomically, so the pair can
 * never be half-applied.
 *
 * Pure and unit-tested so the coordinator stays thin; the caller owns the value this hands
 * back, exactly as it does for [ForegroundWatch]'s pass counter.
 */
object FloatingHold {

    /** How many consecutive passes with no float before the blocker may be released. */
    const val CONFIRMING_PASSES = 3

    /**
     * The hold in force, or the absence of one.
     *
     * A hold is [coveredFor] naming the app whose float the blocker is over, and [misses]
     * counting the re-check passes since the last one that found it. The empty value is a
     * released hold, and is the state a screen-off, a service stop and a satisfied grant all
     * leave behind.
     */
    data class Hold(val coveredFor: String? = null, val misses: Int = 0) {
        /** Whether this hold is still keeping a blocker up. */
        val active: Boolean get() = coveredFor != null
    }

    /** The released hold: nothing floating, nothing counted. */
    val NONE = Hold()

    /**
     * The hold after a pass that found [foundNow].
     *
     * A float found now starts the hold over, whichever app it belongs to: the app a float
     * belongs to can change without the old one ever being reported gone.
     */
    fun afterPass(hold: Hold, foundNow: String?): Hold = when {
        foundNow != null -> Hold(coveredFor = foundNow)
        hold.coveredFor == null -> NONE
        hold.misses + 1 >= CONFIRMING_PASSES -> NONE
        else -> Hold(coveredFor = hold.coveredFor, misses = hold.misses + 1)
    }

    /**
     * Whether a release may be applied while [floating] is on screen, given the hold.
     *
     * [pkg] is the app the release is for, or null when the caller speaks for whatever is in
     * front — the re-check pass, which resolves the launcher over a float and would call it
     * Home. Such a caller may only release when nothing is floating and no hold is standing;
     * a release *for* an app may additionally proceed when that app is the one floating,
     * because granting the app its own time is the whole point of taking the blocker off it.
     *
     * The rule lives in one place because there are three release sites — the re-check pass,
     * the foreground event, and a grant — and every one of them has to ask it. Two did and
     * one did not, and the one that did not is the strobe: a pass that missed the float
     * dismissed on a foreground answer the hold had been raised to overrule.
     */
    fun releaseAllowed(pkg: String?, floating: String?, hold: Hold): Boolean =
        (floating == null || floating == pkg) && (!hold.active || hold.coveredFor == pkg)
}
