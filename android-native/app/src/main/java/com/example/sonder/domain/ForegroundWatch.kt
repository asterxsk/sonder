package com.example.sonder.domain

/**
 * Policy for the blocker's periodic re-check of what is really on screen.
 *
 * The window-event stream is the fast path, but it is not a guarantee: window-state
 * events can be dropped, coalesced or delivered after the fact, and an app that locks
 * itself (an in-app PIN screen, an OEM app-lock activity, a third-party locker's
 * overlay) can put its own window in front of the target without any event for the
 * target at all. Events alone therefore leave a blocked app uncovered for as long as
 * it takes something unrelated to raise a fresh event — the "the app is visible for a
 * few seconds, and sometimes never gets covered" failure.
 *
 * A re-check against the last *resumed activity* cannot miss that: every way into an
 * app — launcher tap, Recents, an app-lock unlock, a screen unlock — resumes an
 * activity, so the blocker converges on the truth within one interval no matter which
 * events were lost. Pure and unit-tested so the coordinator stays thin.
 *
 * The policy is deliberately asymmetric:
 *  - an app on screen is decided on every pass, so a target always ends up under a
 *    blocker even if the pass that would have raised it was the one that got lost;
 *  - a release is only acted on after [CONFIRMING_PASSES] agreeing passes, because the
 *    foreground lookup can trail a launch by a moment and releasing wrongly exposes the
 *    app. A wrongly raised blocker, by contrast, is corrected by the next pass.
 */
object ForegroundWatch {

    /** What one pass must make the platform layer do. */
    enum class Action {
        /** Nothing on screen that this policy speaks for. */
        IGNORE,

        /** Run the normal gate decision for the package that was resolved. */
        DECIDE,

        /** Release the blocker: the user is confirmed to be away from every app. */
        RELEASE,
    }

    /** The action for one pass together with the pass counter it leaves behind. */
    data class Step(val action: Action, val awayPasses: Int)

    /** How many consecutive away-passes a release waits for. */
    const val CONFIRMING_PASSES = 2

    /**
     * @param surface what the resolved foreground package is.
     * @param settled whether what the previous pass decided for this same package still
     *   holds. Only the caller can answer that — it is the one that can see grants and
     *   the blocker window — and its answer is what keeps a live gate from being rebuilt
     *   and a live grant from being re-touched on every pass.
     * @param awayPasses the counter the previous pass left behind.
     */
    fun actionFor(
        surface: ForegroundSurface,
        settled: Boolean,
        awayPasses: Int,
    ): Step = when (surface) {
        ForegroundSurface.APP ->
            if (settled) Step(Action.IGNORE, 0) else Step(Action.DECIDE, 0)

        // Home and the detox app itself: the blocker must not stay up over them. The
        // confirmation is what stops a foreground lookup that still reads "launcher"
        // mid-launch from tearing down a blocker that has only just gone up.
        ForegroundSurface.HOME, ForegroundSurface.OWN -> {
            val confirmed = awayPasses + 1
            Step(
                action = if (confirmed >= CONFIRMING_PASSES) Action.RELEASE else Action.IGNORE,
                awayPasses = confirmed,
            )
        }

        // The shade, an IME, the keyguard, this app's own overlay window: none of them
        // says anything about which app the user is in, so the blocker is left alone.
        ForegroundSurface.TRANSIENT -> Step(Action.IGNORE, 0)
    }

    /**
     * Whether a release-classified foreground event is really the tail of a switch *into*
     * a blocked app rather than the user leaving one.
     *
     * Recents and Home transitions emit window events in bursts, and the launcher's own
     * event — or a lock window's — arrives after the blocked app's often enough that
     * releasing on it uncovers an app the user is looking at. The re-check then puts the
     * blocker back, and that opposition is the flicker: overlay, app, overlay, app.
     *
     * Only a *blocked* app foreground is reason enough to refuse the release. Every other
     * answer — the launcher resolving as foreground, the detox app's own UI, or no
     * answer at all because usage access is unavailable — means the user really did
     * leave, and releasing is what keeps the blocker off Home.
     *
     * @param eventPkg the package whose window reported the release.
     * @param foreground the authoritative foreground package, or null when unknown.
     * @param foregroundBlocked whether [foreground] is an enabled target.
     */
    fun isTrailingRelease(
        eventPkg: String,
        foreground: String?,
        ownPackage: String,
        foregroundBlocked: Boolean,
    ): Boolean = foreground != null &&
        foreground != ownPackage &&
        foreground != eventPkg &&
        foregroundBlocked
}
