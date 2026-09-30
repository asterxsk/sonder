package com.example.sonder.domain

/**
 * Which apps have a short-form video surface, and how to recognise it.
 *
 * The detector reads view identifiers out of the foreground window's node tree (the
 * service does the walking; this file only says what to look for). Identifiers rather
 * than visible text: a resource id is a stable, developer-chosen name, while any text on
 * screen is content, changes with the interface language, and would mean the blocker
 * reading what the user is looking at. Matching is on a *substring* of the identifier,
 * because the exact name is decorated per build (`...:id/reels_viewer`,
 * `...:id/reels_viewer_container`) and the fragment in front of the underscore is the
 * part that has survived Instagram's and YouTube's refactors.
 *
 * This is a heuristic and it is meant to be read as one. It has two failure directions,
 * and they are not symmetric:
 *
 *  - a marker that stops matching means the shorts surface is no longer recognised, so the
 *    app stops being gated there — the user gets their feed back and the target silently
 *    does nothing. Loud in testing, quiet in daily use;
 *  - a marker that over-matches would gate a normal screen in an app the user asked to
 *    keep usable, which is the failure that gets the app uninstalled.
 *
 * The marker lists are therefore kept to names that say *shorts* or *reels* and nothing
 * generic. `reel_recycler` is in; a bare `recycler` or `feed` would be out, however
 * tempting, because those names appear on the screens the user wants left alone.
 *
 * Pure and unit-tested so the service and the coordinator stay thin.
 */
object ShortsCatalog {

    /** Instagram. Reels is a fragment of the main tab activity, so no window event names it. */
    private const val INSTAGRAM = "com.instagram.android"

    /** YouTube. Shorts is likewise reached from within the main activity. */
    private const val YOUTUBE = "com.google.android.youtube"

    /**
     * package → the identifier fragments that mean "the short-form surface is on screen".
     *
     * Read as a case-insensitive substring test against each visible view id in the active
     * window, so the entries are deliberately fragments: `reels_viewer` catches
     * `com.instagram.android:id/reels_viewer` and its per-build decorations alike.
     */
    private val MARKERS: Map<String, List<String>> = mapOf(
        INSTAGRAM to listOf(
            // The Reels viewer itself, in its two spellings: the tab was renamed from
            // "clips" to "reels" and both ids are still live in shipping builds.
            "reels_viewer",
            "clips_viewer",
            // The viewer's own pager, which is present whenever a reel is being watched —
            // including when it is opened from a profile or a link rather than the tab.
            "reel_viewer",
        ),
        YOUTUBE to listOf(
            // The shorts player and its feed. `reel_` is YouTube's own internal name for
            // Shorts; `shorts_` is the newer one.
            "reel_recycler",
            "reel_watch",
            "shorts_container",
            "shorts_player",
        ),
    )

    /**
     * package → what its short-form surface is *called*, in the pixel UI's uppercase.
     *
     * Kept beside [MARKERS] so an entry and its label are written together: the two apps do
     * not share a name for the surface. Instagram's is Reels and YouTube's is Shorts, and a
     * single "REELS & SHORTS" label told every user of either app that they were gating a
     * screen one of the two does not have.
     */
    private val SURFACE_LABELS: Map<String, String> = mapOf(
        INSTAGRAM to "REELS",
        YOUTUBE to "SHORTS",
    )

    /** True when [packageName] is one this catalogue knows how to recognise. */
    fun isCatalogued(packageName: String): Boolean = packageName in MARKERS

    /**
     * Every package this build catalogues.
     *
     * The two tables below are written by hand and keyed by hand, so the one thing that keeps
     * them from drifting apart is that something reads them *together*: the tests walk this
     * set and require a label and a marker list for each entry, which is what fails the build
     * when a third app is added to one table and not the other. Callers in the app use
     * [isCatalogued] instead — this is the set, not a question.
     */
    fun cataloguedPackages(): Set<String> = MARKERS.keys

    /** The identifier fragments to look for in [packageName], or empty when unknown. */
    fun markersFor(packageName: String): List<String> = MARKERS[packageName].orEmpty()

    /**
     * The name of [packageName]'s short-form surface as its own app spells it, or empty for
     * a package with no such surface.
     *
     * Every label the UI shows for a scoped target comes from here, so the scope control and
     * the target row cannot name the same surface two different ways. An empty answer is
     * what a package with no surface gets, and callers that need a label for one must not
     * have offered the choice in the first place — see [isCatalogued], which is what gates
     * the scope control.
     */
    fun surfaceLabelFor(packageName: String): String = SURFACE_LABELS[packageName].orEmpty()

    /**
     * The scope a *new* target for [packageName] starts with.
     *
     * [BlockScope.SHORTS_ONLY] for every catalogued app, which is what "gate the reels, not
     * the app" means as a default: the user adds Instagram to stop losing evenings to
     * Reels, not to be unable to answer a message. Every other app is gated whole, because
     * there is no shorts surface to scope to and a scope with no markers behind it would
     * gate nothing at all.
     *
     * This only sets the starting value. A target the user has already scoped keeps its
     * stored choice — see `TargetDao.setBlockScope`, which names one column and leaves the
     * rest of the row alone.
     */
    fun defaultScopeFor(packageName: String): BlockScope =
        if (isCatalogued(packageName)) BlockScope.SHORTS_ONLY else BlockScope.WHOLE_APP

    /**
     * Whether the ids reported for [packageName]'s active window include its shorts
     * surface.
     *
     * @param viewIds the identifier fragments collected from the window's node tree; the
     *   caller supplies whatever it found, and an empty list simply means "not recognised"
     *   rather than "nothing on screen".
     */
    fun isScopedSurface(packageName: String, viewIds: List<String>): Boolean {
        val markers = markersFor(packageName)
        if (markers.isEmpty()) return false
        return viewIds.any { id ->
            markers.any { marker -> id.contains(marker, ignoreCase = true) }
        }
    }
}
