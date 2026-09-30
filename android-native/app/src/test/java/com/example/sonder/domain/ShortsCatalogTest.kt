package com.example.sonder.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shorts-surface detector, as a pure table. The service's tree walk is not testable
 * here; what it hands this catalogue is, and that is where the two failure directions the
 * KDoc describes actually live.
 */
class ShortsCatalogTest {

    private val instagram = "com.instagram.android"
    private val youtube = "com.google.android.youtube"
    private val facebook = "com.facebook.katana"

    @Test
    fun `the apps with a short-form surface are catalogued`() {
        assertTrue(ShortsCatalog.isCatalogued(instagram))
        assertTrue(ShortsCatalog.isCatalogued(youtube))
        assertTrue(ShortsCatalog.isCatalogued(facebook))
    }

    @Test
    fun `an app with no short-form surface is not catalogued`() {
        assertFalse(ShortsCatalog.isCatalogued("com.example.notes"))
        assertFalse(ShortsCatalog.isCatalogued(""))
    }

    @Test
    fun `new targets for the catalogued apps are scoped to shorts`() {
        assertEquals(BlockScope.SHORTS_ONLY, ShortsCatalog.defaultScopeFor(instagram))
        assertEquals(BlockScope.SHORTS_ONLY, ShortsCatalog.defaultScopeFor(youtube))
        assertEquals(BlockScope.SHORTS_ONLY, ShortsCatalog.defaultScopeFor(facebook))
    }

    @Test
    fun `new targets for everything else gate the whole app`() {
        assertEquals(BlockScope.WHOLE_APP, ShortsCatalog.defaultScopeFor("com.example.notes"))
        assertEquals(BlockScope.WHOLE_APP, ShortsCatalog.defaultScopeFor(""))
    }

    @Test
    fun `a decorated instagram reels id is recognised`() {
        // The shapes the service actually sees: resource ids carry the package and a
        // `type/name` tail, and the name itself is decorated per build.
        assertTrue(
            ShortsCatalog.isScopedSurface(
                instagram,
                listOf("com.instagram.android:id/reels_viewer"),
            ),
        )
        assertTrue(
            ShortsCatalog.isScopedSurface(
                instagram,
                listOf("com.instagram.android:id/clips_viewer_container"),
            ),
        )
    }

    @Test
    fun `a decorated youtube shorts id is recognised`() {
        assertTrue(
            ShortsCatalog.isScopedSurface(
                youtube,
                listOf("com.google.android.youtube:id/reel_recycler"),
            ),
        )
        assertTrue(
            ShortsCatalog.isScopedSurface(
                youtube,
                listOf("com.google.android.youtube:id/shorts_player_view"),
            ),
        )
    }

    @Test
    fun `a decorated facebook reels id is recognised`() {
        assertTrue(
            ShortsCatalog.isScopedSurface(
                facebook,
                listOf("com.facebook.katana:id/reels_viewer"),
            ),
        )
    }

    @Test
    fun `a decorated facebook stories id is recognised`() {
        // The other half of Facebook's surface, and the one whose marker list is easiest to
        // get wrong: the tray of story rings sits on the news feed, so only the viewer counts.
        assertTrue(
            ShortsCatalog.isScopedSurface(
                facebook,
                listOf("com.facebook.katana:id/stories_viewer"),
            ),
        )
    }

    @Test
    fun `matching is case-insensitive`() {
        assertTrue(
            ShortsCatalog.isScopedSurface(
                youtube,
                listOf("com.google.android.youtube:id/Reel_Recycler"),
            ),
        )
    }

    @Test
    fun `the feed is not the shorts surface`() {
        // The failure that matters most: these are the ids on the screens the user asked to
        // keep. A marker loose enough to match any of them would gate the whole app by
        // accident, which is the one outcome worse than not gating at all.
        assertFalse(
            ShortsCatalog.isScopedSurface(
                instagram,
                listOf(
                    "com.instagram.android:id/feed_recycler_view",
                    "com.instagram.android:id/main_feed",
                    "com.instagram.android:id/tab_bar",
                    "com.instagram.android:id/direct_inbox",
                ),
            ),
        )
        assertFalse(
            ShortsCatalog.isScopedSurface(
                youtube,
                listOf(
                    "com.google.android.youtube:id/results",
                    "com.google.android.youtube:id/watch_player",
                    "com.google.android.youtube:id/bottom_bar",
                ),
            ),
        )
        // The stories tray is the Facebook case of the same trap: the rings ride on top of the
        // news feed, so a marker for the tray would gate the feed itself.
        assertFalse(
            ShortsCatalog.isScopedSurface(
                facebook,
                listOf(
                    "com.facebook.katana:id/feed_recycler_view",
                    "com.facebook.katana:id/stories_tray",
                    "com.facebook.katana:id/tab_bar",
                ),
            ),
        )
    }

    @Test
    fun `one matching id among many is enough`() {
        assertTrue(
            ShortsCatalog.isScopedSurface(
                instagram,
                listOf(
                    "com.instagram.android:id/tab_bar",
                    "com.instagram.android:id/reels_viewer",
                    "com.instagram.android:id/feed_recycler_view",
                ),
            ),
        )
    }

    @Test
    fun `an empty tree reports no surface`() {
        // Not "nothing on screen" — nothing recognised. The caller treats both as "not the
        // shorts surface", which is the safe direction: an unrecognised window passes.
        assertFalse(ShortsCatalog.isScopedSurface(instagram, emptyList()))
    }

    @Test
    fun `a package with no markers can never report a surface`() {
        assertFalse(
            ShortsCatalog.isScopedSurface(
                "com.example.notes",
                listOf("com.instagram.android:id/reels_viewer"),
            ),
        )
    }

    @Test
    fun `each catalogued app names its own surface`() {
        // Instagram has Reels, YouTube has Shorts. A combined label named a screen one of
        // the two does not have, which is what the settings screen used to show both of
        // them. Facebook is the one app where the combined label is right, because its
        // surface genuinely is both Reels and Stories.
        assertEquals("REELS", ShortsCatalog.surfaceLabelFor(instagram))
        assertEquals("SHORTS", ShortsCatalog.surfaceLabelFor(youtube))
        assertEquals("REELS & STORIES", ShortsCatalog.surfaceLabelFor(facebook))
    }

    @Test
    fun `a package with no surface has no name for one`() {
        // The UI gates the scope control on isCatalogued, and an empty label is the second
        // signal that there is nothing to offer: a caller that skipped the check draws no
        // option rather than a nameless one.
        assertEquals("", ShortsCatalog.surfaceLabelFor("com.example.notes"))
        assertEquals("", ShortsCatalog.surfaceLabelFor(""))
    }

    @Test
    fun `no marker is repeated inside one app's own list`() {
        // Deliberately per-app rather than across the catalogue. Instagram and Facebook both
        // name their Reels viewer `reels_viewer`, and that fragment is a real one two shipping
        // apps share — a cross-app rule would fail the build over a fact about Instagram, and
        // a duplicate *within* a list is the one that is actually dead weight. Cross-fire is
        // not possible in the first place: `showsMarkers` walks only windows whose root
        // package is the package being asked about, so Instagram's fragment is never tested
        // against Facebook's tree.
        ShortsCatalog.cataloguedPackages().forEach { pkg ->
            val markers = ShortsCatalog.markersFor(pkg)
            assertEquals("$pkg repeats a marker", markers.size, markers.toSet().size)
        }
    }

    @Test
    fun `every catalogued app has a marker list and a label`() {
        // The two tables are hand-written and keyed by hand, so the way they drift apart is
        // a third app added to one and not the other. This walks the *catalogue's own* key
        // set rather than a list repeated here, or it would pass while the app it was meant
        // to guard went unlabelled — an empty tab in the settings screen, and a target row
        // naming a surface with the generic fallback word.
        val catalogued = ShortsCatalog.cataloguedPackages()
        assertEquals(setOf(instagram, youtube, facebook), catalogued)
        catalogued.forEach { pkg ->
            assertTrue(
                "$pkg is catalogued but carries no markers",
                ShortsCatalog.markersFor(pkg).isNotEmpty(),
            )
            assertTrue(
                "$pkg is catalogued but has no surface label",
                ShortsCatalog.surfaceLabelFor(pkg).isNotEmpty(),
            )
        }
    }
}
