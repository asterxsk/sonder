package com.example.sonder.ui.screens.targets

import com.example.sonder.data.db.TargetEntity
import com.example.sonder.data.repo.InstalledApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Targets pure layer: the added-only list, the picker's search and view states,
 * and the ADD-preserves-overrides invariant — all without Room, Compose, or Android.
 */
class TargetsFilterTest {

    private val alpha = TargetPickUi(packageName = "com.example.alpha", label = "Alpha", enabled = true)
    private val beta = TargetPickUi(packageName = "com.example.beta", label = "Beta", enabled = false)

    @Test
    fun `added targets are the enabled picks only`() {
        assertEquals(listOf(alpha), addedTargets(listOf(alpha, beta)))
    }

    @Test
    fun `query is trimmed and lowercased once`() {
        assertEquals("alpha", normalizeTargetsQuery("  ALPHA "))
    }

    @Test
    fun `search matches labels and package names case-insensitively`() {
        assertEquals(listOf(alpha), filterLaunchable(listOf(alpha, beta), normalizeTargetsQuery("ALPHA")))
        assertEquals(listOf(beta), filterLaunchable(listOf(alpha, beta), normalizeTargetsQuery("com.example.BETA")))
        assertEquals(emptyList<TargetPickUi>(), filterLaunchable(listOf(alpha, beta), normalizeTargetsQuery("zzz")))
    }

    @Test
    fun `search keeps added and not-added rows alike`() {
        // The picker lists the whole launchable set; an added app must still be findable
        // so it can read as the inert checked row, not silently vanish.
        assertEquals(listOf(alpha, beta), filterLaunchable(listOf(alpha, beta), ""))
    }

    @Test
    fun `loading is distinct from an empty loaded package list`() {
        val loading = targetsUiState(TargetsListState.Loading)
        val empty = targetsUiState(TargetsListState.Loaded(emptyList()))
        assertEquals(TargetsViewState.Loading, loading)
        assertEquals(TargetsViewState.NoLaunchableApps, empty)
        assertNotEquals(loading, empty)
    }

    @Test
    fun `a failed load is its own state and never reads as no launchable apps`() {
        val failed = targetsUiState(TargetsListState.Failed)
        assertEquals(TargetsViewState.LoadFailed, failed)
        // The two lies this state must not tell: "empty device" and "still reading".
        assertNotEquals(TargetsViewState.NoLaunchableApps, failed)
        assertNotEquals(TargetsViewState.Loading, failed)
    }

    @Test
    fun `loaded apps with nothing enabled is the empty-list state`() {
        assertEquals(TargetsViewState.Empty, targetsUiState(TargetsListState.Loaded(listOf(beta))))
    }

    @Test
    fun `the targets list carries enabled picks only`() {
        val state = targetsUiState(TargetsListState.Loaded(listOf(alpha, beta)))
        assertEquals(TargetsViewState.Rows(listOf(alpha)), state)
    }

    @Test
    fun `picker loading and failure mirror the enumeration state`() {
        assertEquals(TargetPickerViewState.Loading, pickerUiState(TargetsListState.Loading, ""))
        assertEquals(TargetPickerViewState.LoadFailed, pickerUiState(TargetsListState.Failed, ""))
    }

    @Test
    fun `picker distinguishes no launchable apps from no results`() {
        assertEquals(
            TargetPickerViewState.NoLaunchableApps,
            pickerUiState(TargetsListState.Loaded(emptyList()), ""),
        )
        assertEquals(
            TargetPickerViewState.NoResults,
            pickerUiState(TargetsListState.Loaded(listOf(alpha, beta)), normalizeTargetsQuery("zzz")),
        )
    }

    @Test
    fun `picker rows are the filtered launchable set`() {
        val state = pickerUiState(TargetsListState.Loaded(listOf(alpha, beta)), normalizeTargetsQuery("beta"))
        assertEquals(TargetPickerViewState.Rows(listOf(beta)), state)
    }

    @Test
    fun `merge keeps package-name keys and defaults unknown apps to off`() {
        val picks = mergeTargetPicks(
            apps = listOf(
                InstalledApp("com.example.alpha", "Alpha"),
                InstalledApp("com.example.beta", "Beta"),
            ),
            enabledByPackage = mapOf("com.example.alpha" to true),
        )
        assertEquals(listOf(alpha, beta), picks)
    }

    @Test
    fun `add only ever materialises a row, so it cannot overwrite stored overrides`() {
        // The user's named invariant: X only clears enabled, and ADD restores the app with
        // the settings it had. It holds structurally — ADD inserts only where there is no
        // row, and its other write names only `enabled` and `label` — so what the pure
        // layer has to guarantee is that the row it hands to the insert path carries
        // nothing but a fresh enabled target. A null here is the assertion: there is no
        // stored override for it to carry, and none for it to write back over a newer one.
        val added = newTarget("com.example.beta", "Beta", nowMillis = 99L)

        assertEquals("com.example.beta", added.packageName)
        assertEquals("Beta", added.label)
        assertTrue(added.enabled)
        assertEquals(99L, added.createdAtMillis)
        assertNull(added.winGrantMillis)
        assertNull(added.lossDebtMillis)
        assertNull(added.maxDebtMillis)
        assertNull(added.absenceRevokeMillis)
        assertNull(added.dailyCapMillis)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `picker recomputes only when picks or the normalized query change`() = runTest {
        val list = MutableStateFlow<TargetsListState>(TargetsListState.Loaded(listOf(alpha, beta)))
        val query = MutableStateFlow("")
        val seen = mutableListOf<TargetPickerViewState>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            pickerUiFlow(list, query).collect { seen += it }
        }
        runCurrent()

        query.value = "alpha" // normalizes to "alpha" -> recompute
        runCurrent()
        query.value = "alpha " // normalizes to "alpha" again -> suppressed
        runCurrent()
        list.value = TargetsListState.Loaded(listOf(alpha)) // -> recompute
        runCurrent()

        // Initial, the "alpha" query, and the shorter list — the trailing space is not one.
        assertEquals(3, seen.size)
        assertTrue(seen.last() is TargetPickerViewState.Rows)
    }
}
