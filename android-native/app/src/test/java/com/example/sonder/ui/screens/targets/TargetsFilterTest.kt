package com.example.sonder.ui.screens.targets

import com.example.sonder.data.repo.InstalledApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every "Targets filter" bullet from the design's test list, plus the states it names. */
class TargetsFilterTest {

    private val alpha = TargetPickUi(packageName = "com.example.alpha", label = "Alpha", enabled = true)
    private val beta = TargetPickUi(packageName = "com.example.beta", label = "Beta", enabled = false)

    @Test
    fun `all tab includes enabled and disabled launchable apps`() {
        val rows = filterTargets(listOf(alpha, beta), TargetsTab.ALL, "")
        assertEquals(listOf(alpha, beta), rows)
    }

    @Test
    fun `limited tab includes enabled apps only`() {
        val rows = filterTargets(listOf(alpha, beta), TargetsTab.LIMITED, "")
        assertEquals(listOf(alpha), rows)
    }

    @Test
    fun `query matches labels and package names case-insensitively`() {
        // Label match, query case differs.
        assertEquals(
            listOf(alpha),
            filterTargets(listOf(alpha, beta), TargetsTab.ALL, normalizeTargetsQuery("ALPHA")),
        )
        // Package-name match, label does not contain the query.
        assertEquals(
            listOf(beta),
            filterTargets(listOf(alpha, beta), TargetsTab.ALL, normalizeTargetsQuery("com.example.BETA")),
        )
        // LIMITED still drops the disabled match.
        assertEquals(
            emptyList<TargetPickUi>(),
            filterTargets(listOf(alpha, beta), TargetsTab.LIMITED, normalizeTargetsQuery("beta")),
        )
    }

    @Test
    fun `nonmatching query produces the no-results state`() {
        val state = targetsUiState(
            TargetsListState.Loaded(listOf(alpha, beta)),
            TargetsTab.ALL,
            normalizeTargetsQuery("zzz"),
        )
        assertEquals(TargetsViewState.NoResults, state.view)
    }

    @Test
    fun `loading is distinct from an empty loaded package list`() {
        val loading = targetsUiState(TargetsListState.Loading, TargetsTab.ALL, "")
        val empty = targetsUiState(TargetsListState.Loaded(emptyList()), TargetsTab.ALL, "")
        assertEquals(TargetsViewState.Loading, loading.view)
        assertEquals(TargetsViewState.NoLaunchableApps, empty.view)
        assertNotEquals(loading.view, empty.view)
    }

    @Test
    fun `a failed load is its own state and never reads as no launchable apps`() {
        val allTab = targetsUiState(TargetsListState.Failed, TargetsTab.ALL, "")
        assertEquals(TargetsViewState.LoadFailed, allTab.view)
        // The two lies this state must not tell: "empty device" and "still reading".
        assertNotEquals(TargetsViewState.NoLaunchableApps, allTab.view)
        assertNotEquals(TargetsViewState.Loading, allTab.view)
        // No list means no honest count to show on the LIMITED tab either.
        val limitedTab = targetsUiState(TargetsListState.Failed, TargetsTab.LIMITED, "")
        assertEquals(TargetsViewState.LoadFailed, limitedTab.view)
        assertEquals(0, limitedTab.enabledCount)
    }

    @Test
    fun `empty limited filter is its own state`() {
        val state = targetsUiState(TargetsListState.Loaded(listOf(beta)), TargetsTab.LIMITED, "")
        assertEquals(TargetsViewState.EmptyLimited, state.view)
    }

    @Test
    fun `enabled count ignores the tab and the query`() {
        val state = targetsUiState(TargetsListState.Loaded(listOf(alpha, beta)), TargetsTab.LIMITED, "beta")
        assertEquals(1, state.enabledCount)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `ui recomputes only when picks tab or normalized query change`() = runTest {
        val list = MutableStateFlow<TargetsListState>(TargetsListState.Loaded(listOf(alpha, beta)))
        val tab = MutableStateFlow(TargetsTab.ALL)
        val query = MutableStateFlow("")
        val seen = mutableListOf<TargetsUiState>()

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            targetsUiFlow(list, tab, query).collect { seen += it }
        }
        runCurrent()

        query.value = "alpha" // normalizes to "alpha" -> recompute
        runCurrent()
        query.value = "alpha " // normalizes to "alpha" again -> suppressed
        runCurrent()
        tab.value = TargetsTab.LIMITED // -> recompute
        runCurrent()

        // Initial, the "alpha" query, and the LIMITED tab — the trailing space is not one.
        assertEquals(3, seen.size)
        assertTrue(seen.last().view is TargetsViewState.Rows)
    }
}
