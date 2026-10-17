package com.example.sonder.domain

/**
 * How long the blocker holds on to a picture-in-picture window it was raised over.
 *
 * A gated app that is playing in a float is the one case the gate cannot settle by itself: the
 * float is not a window-state event for the app's activity, so the event stream names nothing,
 * and the two probes that could name it disagree across the moment the app changes windows —
 * the window list is read while the transition is still happening, and the usage-event lookup
 * the release path decides on is batched by the platform. One pass covers the float, the next
 * sees no float and releases, and the user watches the blocker strobe over the video they are
 * still watching.
 *
 * So a release is counted rather than accepted: [CONFIRMING_PASSES] passes in a row must
 * answer *no* before the hold ends. The asymmetry matches [ForegroundWatch]'s — releasing
 * wrongly exposes an app the user is watching, while holding a pass or two too long only
 * delays the correction, and a run of misses, once started, needs nothing further to finish:
 * only the pass stream feeds the counter.
 *
 * That is not the same as saying a hold cannot outlast the float, and it must not be read
 * that way — a run needs a *readable* pass to advance at all, so a probe that goes blind and
 * stays blind keeps the blocker up for as long as the blindness lasts. That is the intended
 * trade and not a trap: it is the direction that costs a delayed correction rather than an
 * uncovered float, and the states a stalled probe could otherwise outlive are the ones the
 * coordinator clears the hold for outright, without any pass at all: a screen-off, a service
 * stop and a fresh connection. A fourth is a satisfied grant, which clears it only when the
 * granted app is the one the hold is over — the user has paid for that float, so nothing is
 * left for the rule to hold, and it must be cleared rather than aged because a *paid* float
 * is deliberately not reported and so nothing readable is coming to age it. A grant for a
 * different app leaves the hold standing, because that float is still unpaid.
 *
 *
 * **Absence is an answer, and an unreadable window list is not one.** The probe has three
 * outcomes and the hold treats them as three: a float was seen ([Answer.Found]), the app was
 * asked about and answered that no small window of its own is on screen ([Answer.None]), or the
 * probe could not read the list at all ([Answer.Unknown]). Collapsing the last two is the bug
 * this rule was rewritten for — the probe is starved exactly when the service's own thread is
 * busy, which is precisely when a Short is in the float, so "could not look" arrived as
 * "nothing there", three of those cleared the hold, and the blocker came off the video it was
 * covering. Only [Answer.None] advances the counter now; [Answer.Unknown] holds, and can never
 * release anything on its own.
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

    /** How many consecutive passes answering [Answer.None] before the blocker may be released. */
    const val CONFIRMING_PASSES = 3

    /**
     * What one float probe said about the app it was asked about.
     *
     * The three cases are deliberately not a nullable package: "no" and "could not tell" have
     * to be different values or the rule above cannot exist.
     */
    sealed interface Answer {

        /** A float of [packageName] is on screen. */
        data class Found(val packageName: String) : Answer

        /** The app was asked and has no small window on screen. The only answer that counts. */
        data object None : Answer

        /** The window list could not be read. Never evidence of absence. */
        data object Unknown : Answer
    }

    /**
     * The hold in force, or the absence of one.
     *
     * A hold is [coveredFor] naming the app whose float the blocker is over, and [misses]
     * counting the re-check passes that have answered [Answer.None] since the last one that
     * found it. The empty value is a released hold, and is the state a screen-off, a service
     * stop and a grant for the app it covers all leave behind.
     */
    data class Hold(val coveredFor: String? = null, val misses: Int = 0) {
        /** Whether this hold is still keeping a blocker up. */
        val active: Boolean get() = coveredFor != null
    }

    /** The released hold: nothing floating, nothing counted. */
    val NONE = Hold()

    /**
     * The hold after a pass that answered [answer].
     *
     * A float found now starts the hold over, whichever app it belongs to: the app a float
     * belongs to can change without the old one ever being reported gone.
     *
     * [Answer.Unknown] neither starts nor ends a hold and does not advance the counter: a pass
     * that could not read the window list has been told nothing, and the counter is the only
     * thing that can end a hold, so counting a blind pass would let three blind passes release
     * a blocker over a float nobody managed to look at. The hold is returned *unchanged* —
     * misses included — because a blind pass is not a step of the count in either direction.
     */
    fun afterPass(hold: Hold, answer: Answer): Hold = when (answer) {
        is Answer.Found -> Hold(coveredFor = answer.packageName)
        Answer.Unknown -> hold
        Answer.None -> when {
            hold.coveredFor == null -> NONE
            hold.misses + 1 >= CONFIRMING_PASSES -> NONE
            else -> Hold(coveredFor = hold.coveredFor, misses = hold.misses + 1)
        }
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
     * The rule lives in one place because every release site in the coordinator has to ask it —
     * the re-check pass, the foreground event's Home/own branch, the pass branch of a gate
     * decision, and a grant — and a site that does not is the strobe: a pass that missed the
     * float dismissing on a foreground answer the hold had been raised to overrule. That is not
     * hypothetical; it is what one of them did before this rule was shared.
     *
     * **A caller that speaks for whatever is in front is held to the strict form**, and that
     * asymmetry is the whole point of the parameter. Such a caller is responding to whatever
     * the *foreground* probe named, which over a float is the launcher — so its answer is not
     * evidence about the float at all, and an active hold refuses it outright rather than
     * merely requiring the float to be named. The looser form is for callers that name an app,
     * where "is it the floating one" is a question that can be answered.
     *
     * The two branches are written out separately because they are *two rules*, not one rule
     * with a loophole in it. The single expression this replaced already happened to evaluate
     * the strict case correctly — with `pkg == null` it collapsed to the same conjunction — so
     * this is a spelling-out and not a behaviour change, and the case it protects is the one
     * that expression made easy to misread: an active hold must refuse a launcher-flavoured
     * release even when the float probe has just missed its float, or the blocker comes off
     * the video three times a second.
     */
    fun releaseAllowed(pkg: String?, floating: String?, hold: Hold): Boolean =
        if (pkg == null) {
            floating == null && !hold.active
        } else {
            (floating == null || floating == pkg) && (!hold.active || hold.coveredFor == pkg)
        }
}
