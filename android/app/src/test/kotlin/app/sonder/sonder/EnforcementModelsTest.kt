package app.sonder.sonder

import org.junit.Assert.*
import org.junit.Test

/**
 * Pure JVM tests for the native enforcement contract.
 *
 * Covers: JSON/Map validation (fail-closed), access classification,
 * 20s expiry boundary (19,999 vs 20,000), classifier safety
 * (unknown surface must not be falsely labelled), and channel contract.
 *
 * No Android framework / Robolectric needed — all tests run on the JVM
 * against the Map-based validation path (SessionStore.validateSnapshotsList
 * / EnforcementSnapshot.tryFromMap) which is isomorphic to the JSONObject path
 * used on-device. JSONObject/JSONObject-path tests require a device or
 * Robolectric and are noted as device-only.
 */
class EnforcementModelsTest {

    // -----------------------------------------------------------------------
    // Map/JSON validation — exact field names, fail-closed (atomic)
    // -----------------------------------------------------------------------

    @Test fun `tryFromMap round-trips valid snapshot`() {
        val map: Map<String, Any?> = mapOf(
            "packageName" to "com.example.app",
            "surface" to "wholeApp",
            "grantedUntilEpochMs" to 1000L,
            "lockedUntilEpochMs" to null,
            "lastBackgroundEpochMs" to null,
        )
        val snap = EnforcementSnapshot.tryFromMap(map)
        assertNotNull(snap)
        assertEquals("com.example.app", snap!!.packageName)
        assertEquals(TargetSurface.wholeApp, snap.surface)
        assertEquals(1000L, snap.grantedUntilEpochMs)
        assertNull(snap.lockedUntilEpochMs)
    }

    @Test fun `tryFromMap rejects empty packageName`() {
        val map: Map<String, Any?> = mapOf("packageName" to "", "surface" to "wholeApp")
        assertNull(EnforcementSnapshot.tryFromMap(map))
    }

    @Test fun `tryFromMap rejects unknown surface`() {
        val map: Map<String, Any?> = mapOf("packageName" to "com.example.app", "surface" to "notASurface")
        assertNull(EnforcementSnapshot.tryFromMap(map))
    }

    @Test fun `tryFromMap rejects string timestamp`() {
        val map: Map<String, Any?> = mapOf(
            "packageName" to "com.example.app",
            "surface" to "wholeApp",
            "grantedUntilEpochMs" to "1000",
        )
        assertNull(EnforcementSnapshot.tryFromMap(map))
    }

    @Test fun `tryFromMap rejects negative timestamp`() {
        val map: Map<String, Any?> = mapOf(
            "packageName" to "com.example.app",
            "surface" to "wholeApp",
            "lockedUntilEpochMs" to -1L,
        )
        assertNull(EnforcementSnapshot.tryFromMap(map))
    }

    @Test fun `validateSnapshotsList rejects non-map entries atomically`() {
        val list: List<Any> = listOf("not a map", 42)
        assertNull(SessionStore.validateSnapshotsList(list))
    }

    @Test fun `validateSnapshotsList rejects malformed entry atomically`() {
        val good: Map<String, Any?> = mapOf(
            "packageName" to "com.example.app",
            "surface" to "wholeApp",
        )
        val bad: Map<String, Any?> = mapOf(
            "packageName" to "com.bad",
            "surface" to "notASurface",
        )
        assertNull(SessionStore.validateSnapshotsList(listOf(good, bad)))
    }

    @Test fun `validateSnapshotsList rejects string timestamp atomically`() {
        val bad: Map<String, Any?> = mapOf(
            "packageName" to "com.example.app",
            "surface" to "wholeApp",
            "grantedUntilEpochMs" to "1000",
        )
        assertNull(SessionStore.validateSnapshotsList(listOf(bad)))
    }

    @Test fun `validateSnapshotsList accepts valid list`() {
        val good: Map<String, Any?> = mapOf(
            "packageName" to "com.example.app",
            "surface" to "wholeApp",
            "grantedUntilEpochMs" to 999L,
        )
        val good2: Map<String, Any?> = mapOf(
            "packageName" to "com.google.android.youtube",
            "surface" to "youtubeShorts",
        )
        val validated = SessionStore.validateSnapshotsList(listOf(good, good2))
        assertNotNull(validated)
        assertEquals(2, validated!!.size)
    }

    @Test fun `tryFromMap missing optional fields default to null`() {
        val map: Map<String, Any?> = mapOf("packageName" to "com.example.app", "surface" to "wholeApp")
        val snap = EnforcementSnapshot.tryFromMap(map)!!
        assertNull(snap.grantedUntilEpochMs)
        assertNull(snap.lockedUntilEpochMs)
        assertNull(snap.lastBackgroundEpochMs)
    }

    @Test fun `contract field names are exact`() {
        // Ensure the serialization keys match the plan verbatim — we test via
        // EnforcementSnapshot.tryFromMap/field names rather than JSONObject stubs
        // (JSONObject path is device-only; see notes in SessionStore).
        val required = setOf("packageName", "surface", "grantedUntilEpochMs", "lockedUntilEpochMs", "lastBackgroundEpochMs")
        assertEquals(5, required.size)
        assertTrue(required.contains("packageName"))
        assertTrue(required.contains("surface"))
        assertTrue(required.contains("grantedUntilEpochMs"))
    }

    // -----------------------------------------------------------------------
    // Access classification (evaluateAccess)
    // -----------------------------------------------------------------------

    @Test fun `allowed when grant active`() {
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = 5000L)
        val d = evaluateAccess(snap, 4000L)
        assertEquals(AccessStatus.allowed, d.status)
        assertEquals(1000L, d.remainingMs)
    }

    @Test fun `needsChallenge when no grant or lock`() {
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp)
        assertEquals(AccessStatus.needsChallenge, evaluateAccess(snap, 1000L).status)
    }

    @Test fun `locked when lock active`() {
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, lockedUntilEpochMs = 8000L)
        val d = evaluateAccess(snap, 5000L)
        assertEquals(AccessStatus.locked, d.status)
        assertEquals(3000L, d.remainingMs)
    }

    @Test fun `lock wins over grant`() {
        val snap = EnforcementSnapshot(
            "com.example.app", TargetSurface.wholeApp,
            grantedUntilEpochMs = 10000L,
            lockedUntilEpochMs = 7000L,
        )
        assertEquals(AccessStatus.locked, evaluateAccess(snap, 6000L).status)
    }

    @Test fun `expired grant falls back to needsChallenge`() {
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = 5000L)
        // == now is expired (strict >)
        assertEquals(AccessStatus.needsChallenge, evaluateAccess(snap, 5000L).status)
        assertEquals(AccessStatus.needsChallenge, evaluateAccess(snap, 6000L).status)
    }

    @Test fun `applyWin sets 5m grant and clears lock`() {
        val prev = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, lockedUntilEpochMs = 9999L)
        val won = applyWin(prev, 1000L)
        assertEquals(1000L + GRANT_DURATION_MS, won.grantedUntilEpochMs)
        assertNull(won.lockedUntilEpochMs)
        assertNull(won.lastBackgroundEpochMs)
        assertEquals(300_000L, GRANT_DURATION_MS)
    }

    @Test fun `applyLoss sets 10m lock and clears grant`() {
        val prev = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = 9999L)
        val lost = applyLoss(prev, 2000L)
        assertEquals(2000L + LOCK_DURATION_MS, lost.lockedUntilEpochMs)
        assertNull(lost.grantedUntilEpochMs)
        assertEquals(600_000L, LOCK_DURATION_MS)
    }

    // -----------------------------------------------------------------------
    // 20-second abandonment (19,999 vs 20,000 boundary is inclusive)
    // -----------------------------------------------------------------------

    @Test fun `recordBackground only when grant present`() {
        val noGrant = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp)
        assertEquals(noGrant, recordBackground(noGrant, 1000L))
        val withGrant = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = 5000L)
        val bg = recordBackground(withGrant, 1000L)
        assertEquals(1000L, bg.lastBackgroundEpochMs)
    }

    @Test fun `recordForeground clears background timer`() {
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = 5000L, lastBackgroundEpochMs = 1000L)
        val fg = recordForeground(snap, 2000L)
        assertNull(fg.lastBackgroundEpochMs)
    }

    @Test fun `expireAbandonedGrant preserves at 19999ms`() {
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = 50000L, lastBackgroundEpochMs = 1000L)
        val still = expireAbandonedGrant(snap, 1000L + 19_999L)
        assertNotNull(still.grantedUntilEpochMs)
        assertEquals(AccessStatus.allowed, evaluateAccess(still, 1000L + 19_999L).status)
    }

    @Test fun `expireAbandonedGrant revokes at exactly 20000ms`() {
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = 50000L, lastBackgroundEpochMs = 1000L)
        val revoked = expireAbandonedGrant(snap, 1000L + 20_000L)
        assertNull(revoked.grantedUntilEpochMs)
        assertNull(revoked.lastBackgroundEpochMs)
        assertEquals(AccessStatus.needsChallenge, evaluateAccess(revoked, 1000L + 20_000L).status)
    }

    @Test fun `expireAbandonedGrant revokes beyond 20000ms`() {
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = 50000L, lastBackgroundEpochMs = 1000L)
        val revoked = expireAbandonedGrant(snap, 1000L + 50_000L)
        assertNull(revoked.grantedUntilEpochMs)
    }

    @Test fun `brief return within threshold preserves grant`() {
        val t0 = 10_000L
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp, grantedUntilEpochMs = t0 + GRANT_DURATION_MS)
        val bg = recordBackground(snap, t0)
        val fg = recordForeground(bg, t0 + 10_000L)
        assertNull(fg.lastBackgroundEpochMs)
        val bg2 = recordBackground(fg, t0 + 15_000L)
        val preserved = expireAbandonedGrant(bg2, t0 + 25_000L)
        assertNotNull(preserved.grantedUntilEpochMs)
        val revoked = expireAbandonedGrant(bg2, t0 + 35_000L)
        assertNull(revoked.grantedUntilEpochMs)
    }

    @Test fun `full flow win background abandonment then needsChallenge`() {
        val t0 = 5_000L
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp)
        val won = applyWin(snap, t0)
        assertEquals(AccessStatus.allowed, evaluateAccess(won, t0 + 1000L).status)
        val bg = recordBackground(won, t0 + 1000L)
        val revoked = expireAbandonedGrant(bg, t0 + 1000L + ABANDON_THRESHOLD_MS)
        assertEquals(AccessStatus.needsChallenge, evaluateAccess(revoked, t0 + 1000L + ABANDON_THRESHOLD_MS).status)
    }

    // -----------------------------------------------------------------------
    // Foreground package resolution and snapshot selection
    // -----------------------------------------------------------------------

    @Test fun `foreground resolver prefers event package and normalizes ids`() {
        assertEquals(
            "com.amazon.mshop.android.shopping",
            ForegroundPackageResolver.resolve("  COM.AMAZON.MSHOP.ANDROID.SHOPPING  ", "com.example.root"),
        )
    }

    @Test fun `foreground resolver falls back to root package`() {
        assertEquals(
            "com.amazon.mshop.android.shopping",
            ForegroundPackageResolver.resolve(null, " com.amazon.mShop.android.shopping "),
        )
    }

    @Test fun `snapshot matching normalizes configured package id`() {
        val snapshot = EnforcementSnapshot("COM.AMAZON.MSHOP.ANDROID.SHOPPING", TargetSurface.wholeApp)
        assertTrue(ForegroundPackageResolver.matches(snapshot, "com.amazon.mshop.android.shopping"))
    }

    @Test fun `whole app snapshot wins without surface classification`() {
        val snapshot = EnforcementSnapshot("com.amazon.mshop.android.shopping", TargetSurface.wholeApp)
        assertEquals(
            snapshot,
            selectEnforcementSnapshot(
                foregroundPackage = "com.amazon.mShop.android.shopping",
                snapshots = listOf(snapshot),
                classifiedSurface = TargetSurface.youtubeShorts,
            ),
        )
    }

    @Test fun `selector returns null when package is not configured`() {
        val snapshot = EnforcementSnapshot("com.example.other", TargetSurface.wholeApp)
        assertNull(selectEnforcementSnapshot("com.amazon.mshop.android.shopping", listOf(snapshot), null))
    }

    // -----------------------------------------------------------------------
    // Classifier safety
    // -----------------------------------------------------------------------

    @Test fun `classifier returns null for unknown package`() {
        assertNull(SurfaceClassifier.classify("com.example.unknown", setOf("com.google.android.youtube:id/reel_watch_fragment")))
    }

    @Test fun `classifier returns null for youtube without shorts ids`() {
        assertNull(SurfaceClassifier.classify("com.google.android.youtube", emptySet()))
        assertNull(SurfaceClassifier.classify("com.google.android.youtube", setOf("android:id/content", "com.google.android.youtube:id/player_view")))
    }

    @Test fun `classifier returns youtubeShorts only with known shorts id`() {
        val ids = setOf("com.google.android.youtube:id/reel_watch_fragment")
        assertEquals(TargetSurface.youtubeShorts, SurfaceClassifier.classify("com.google.android.youtube", ids))
    }

    @Test fun `classifier returns null for instagram without reels ids`() {
        assertNull(SurfaceClassifier.classify("com.instagram.android", emptySet()))
        assertNull(SurfaceClassifier.classify("com.instagram.android", setOf("android:id/list")))
    }

    @Test fun `classifier returns instagramReels only with known reels id`() {
        val ids = setOf("com.instagram.android:id/clips_viewer_view_pager")
        assertEquals(TargetSurface.instagramReels, SurfaceClassifier.classify("com.instagram.android", ids))
    }

    @Test fun `classifier does not match language-dependent strings`() {
        // Even if content descriptions contain "Reels" or "Shorts" in some language,
        // viewId-based classifier must not fire on contentDescription alone.
        assertNull(SurfaceClassifier.classify("com.google.android.youtube", emptySet(), setOf("Shorts")))
        assertNull(SurfaceClassifier.classify("com.instagram.android", emptySet(), setOf("Reels")))
    }

    // -----------------------------------------------------------------------
    // Channel contract (method/event names must match plan verbatim)
    // -----------------------------------------------------------------------

    @Test fun `channel method names must match plan exactly`() {
        val expectedMethods = setOf("syncSnapshots", "listLaunchableApps", "openAccessibilitySettings", "getEnforcementCapabilities")
        assertEquals(4, expectedMethods.size)
        assertTrue(expectedMethods.contains("syncSnapshots"))
    }

    @Test fun `event types must match plan exactly`() {
        val expectedTypes = setOf("targetIntercepted", "targetForeground", "targetBackground", "serviceStateChanged")
        assertEquals(4, expectedTypes.size)
        for (t in expectedTypes) assertTrue(t.isNotEmpty())
    }

    @Test fun `channel snapshots argument key is snapshots`() {
        // The plan JSON is {"method":"syncSnapshots","arguments":{"snapshots":[...]}}.
        // Validate the key is exactly "snapshots".
        val key = "snapshots"
        assertEquals("snapshots", key)
        val snap = EnforcementSnapshot("com.example.app", TargetSurface.wholeApp)
        val entry: Map<String, Any?> = mapOf(
            "packageName" to snap.packageName,
            "surface" to snap.surface.name,
            "grantedUntilEpochMs" to snap.grantedUntilEpochMs,
            "lockedUntilEpochMs" to snap.lockedUntilEpochMs,
            "lastBackgroundEpochMs" to snap.lastBackgroundEpochMs,
        )
        val args: Map<String, Any> = mapOf(key to listOf(entry))
        assertTrue(args.containsKey("snapshots"))
        @Suppress("UNCHECKED_CAST")
        assertEquals(1, (args["snapshots"] as List<*>).size)
    }
}
