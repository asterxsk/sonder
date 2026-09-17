package com.example.sonder.ui.screens.targets

import com.example.sonder.data.repo.InstalledApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The two Targets filters: ALL keeps every launchable app, LIMITED keeps the enabled ones. */
enum class TargetsTab { ALL, LIMITED }

/**
 * Launcher enumeration state. [Loading] is deliberately distinct from
 * `Loaded(emptyList())` — the first is "still reading", the second is a device
 * with nothing launchable. [Failed] is a third thing again: the enumeration threw,
 * so there is no list to believe either way.
 */
sealed interface TargetsListState {
    data object Loading : TargetsListState
    data class Loaded(val picks: List<TargetPickUi>) : TargetsListState
    data object Failed : TargetsListState
}

/** What the picker draws once the tab and query are applied. */
sealed interface TargetsViewState {
    /** Packages still enumerating — a spinner, never an unexplained blank list. */
    data object Loading : TargetsViewState

    /** Enumeration finished with nothing launchable on the device. */
    data object NoLaunchableApps : TargetsViewState

    /** Enumeration threw — unknown list, not an empty one, and retryable. */
    data object LoadFailed : TargetsViewState

    /** LIMITED tab, apps are launchable but none is enabled yet. */
    data object EmptyLimited : TargetsViewState

    /** A query matched nothing in the current tab. */
    data object NoResults : TargetsViewState

    /** Rows to draw. */
    data class Rows(val picks: List<TargetPickUi>) : TargetsViewState
}

/** One Targets screen state; recomputed only when its inputs actually change. */
data class TargetsUiState(
    val tab: TargetsTab,
    val enabledCount: Int,
    val view: TargetsViewState,
)

/** Trim and lowercase once so every row comparison downstream is allocation-free. */
fun normalizeTargetsQuery(raw: String): String = raw.trim().lowercase()

/** Enabled picks across the whole list — independent of the tab and the query. */
fun enabledTargetCount(picks: List<TargetPickUi>): Int = picks.count { it.enabled }

/**
 * Applies the tab and [normalizedQuery]. ALL keeps enabled and disabled apps;
 * LIMITED keeps enabled only. The query matches the label or the package name,
 * case-insensitively.
 */
fun filterTargets(
    picks: List<TargetPickUi>,
    tab: TargetsTab,
    normalizedQuery: String,
): List<TargetPickUi> = picks.filter { pick ->
    (tab == TargetsTab.ALL || pick.enabled) &&
        (normalizedQuery.isEmpty() ||
            pick.label.contains(normalizedQuery, ignoreCase = true) ||
            pick.packageName.contains(normalizedQuery, ignoreCase = true))
}

/** Merges launchable apps with Room's enabled flags, keyed by package name. */
fun mergeTargetPicks(
    apps: List<InstalledApp>,
    enabledByPackage: Map<String, Boolean>,
): List<TargetPickUi> = apps.map { app ->
    TargetPickUi(
        packageName = app.packageName,
        label = app.label,
        enabled = enabledByPackage[app.packageName] ?: false,
    )
}

/** Pure mapping from list + tab + normalized query to the screen's render state. */
fun targetsUiState(
    list: TargetsListState,
    tab: TargetsTab,
    normalizedQuery: String,
): TargetsUiState = when (list) {
    TargetsListState.Loading -> TargetsUiState(tab, 0, TargetsViewState.Loading)

    // No list to filter and no enabled count to claim — the count would be a guess.
    TargetsListState.Failed -> TargetsUiState(tab, 0, TargetsViewState.LoadFailed)

    is TargetsListState.Loaded -> {
        val filtered = filterTargets(list.picks, tab, normalizedQuery)
        val view = when {
            list.picks.isEmpty() -> TargetsViewState.NoLaunchableApps
            filtered.isNotEmpty() -> TargetsViewState.Rows(filtered)
            normalizedQuery.isNotEmpty() -> TargetsViewState.NoResults
            else -> TargetsViewState.EmptyLimited
        }
        TargetsUiState(tab, enabledTargetCount(list.picks), view)
    }
}

/**
 * Combines the streams so the count and filter above run only when the picks,
 * the selected tab, or the normalized query changes — a trailing space in the
 * search field, for instance, neither re-filters nor re-emits.
 */
fun targetsUiFlow(
    list: Flow<TargetsListState>,
    tab: Flow<TargetsTab>,
    rawQuery: Flow<String>,
): Flow<TargetsUiState> = combine(
    list,
    tab,
    rawQuery.map(::normalizeTargetsQuery).distinctUntilChanged(),
) { state, selectedTab, normalizedQuery ->
    targetsUiState(state, selectedTab, normalizedQuery)
}
