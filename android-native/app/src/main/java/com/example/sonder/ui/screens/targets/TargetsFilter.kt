package com.example.sonder.ui.screens.targets

import com.example.sonder.data.db.TargetEntity
import com.example.sonder.data.repo.InstalledApp
import com.example.sonder.domain.BlockScope
import com.example.sonder.domain.ShortsCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Launcher enumeration state, shared by both screens. [Loading] is deliberately
 * distinct from `Loaded(emptyList())` — the first is "still reading", the second a
 * device with nothing launchable. [Failed] is a third thing again: the enumeration
 * threw, so there is no list to believe either way and neither screen may claim an
 * empty device.
 */
sealed interface TargetsListState {
    data object Loading : TargetsListState
    data class Loaded(val picks: List<TargetPickUi>) : TargetsListState
    data object Failed : TargetsListState
}

/**
 * What the Targets screen draws. The tab and search dimensions are gone — the screen
 * is the added set — so its states are only "reading", "could not read", "nothing
 * launchable to add", "nothing added yet", or the rows.
 */
sealed interface TargetsViewState {
    data object Loading : TargetsViewState

    /** Nothing on the device has a launcher, so there is nothing ADD could ever offer. */
    data object NoLaunchableApps : TargetsViewState

    /** Enumeration threw — unknown list, not an empty one, and retryable. */
    data object LoadFailed : TargetsViewState

    /** Apps exist, but none is enabled: the empty-list state that offers ADD. */
    data object Empty : TargetsViewState

    /** The added targets to draw. */
    data class Rows(val picks: List<TargetPickUi>) : TargetsViewState
}

/**
 * What the picker draws once its query is applied. Every state the Targets list used
 * to carry lives here now, minus the tab: loading, failed, a device with nothing
 * launchable, a query that matched nothing, or rows.
 */
sealed interface TargetPickerViewState {
    data object Loading : TargetPickerViewState
    data object NoLaunchableApps : TargetPickerViewState
    data object LoadFailed : TargetPickerViewState
    data object NoResults : TargetPickerViewState
    data class Rows(val picks: List<TargetPickUi>) : TargetPickerViewState
}

/** Trim and lowercase once so every row comparison downstream is allocation-free. */
fun normalizeTargetsQuery(raw: String): String = raw.trim().lowercase()

/**
 * The line a row shows for a target that does not gate the whole app, or null for one that
 * does — null rather than a "WHOLE APP" label, because that is the default and labelling the
 * default on every row is noise. One definition, used by the Targets row and the app's
 * settings screen, so the two cannot describe the same scope differently.
 */
fun blockScopeNote(scope: BlockScope): String? = when (scope) {
    BlockScope.WHOLE_APP -> null
    BlockScope.SHORTS_ONLY -> "REELS & SHORTS ONLY"
}

/**
 * The added apps, in list order — enabled targets only. A soft-removed row
 * (`enabled = false`) keeps its overrides in Room but is no longer a target, so it
 * must not appear here; re-adding it through the picker brings it back.
 */
fun addedTargets(picks: List<TargetPickUi>): List<TargetPickUi> = picks.filter { it.enabled }

/**
 * The picker's search: matches label or package, case-insensitively, over the whole
 * launchable list — added and not-added alike, so an added app can still be found and
 * read as the inert checked row that says removal lives on Targets.
 */
fun filterLaunchable(
    picks: List<TargetPickUi>,
    normalizedQuery: String,
): List<TargetPickUi> = picks.filter { pick ->
    normalizedQuery.isEmpty() ||
        pick.label.contains(normalizedQuery, ignoreCase = true) ||
        pick.packageName.contains(normalizedQuery, ignoreCase = true)
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

/**
 * The row ADD inserts when [packageName] has no stored row at all. It is deliberately
 * inserted, never upserted: a soft-removed row that still holds the user's per-app
 * overrides must keep them, so ADD's other half is a write naming only `enabled` and
 * `label` (`TargetDao.enable`). Together the two can express "make this a target and
 * refresh its name" without ever rewriting a column this screen does not own — which is
 * what a read-copy-write of the whole row could not promise, since the copy is stale the
 * moment the settings screen saves a knob.
 *
 * Carrying no overrides is the point: this entity is only ever inserted where there was
 * nothing, so there is nothing for its nulls to overwrite.
 *
 * [ShortsCatalog.defaultScopeFor] is the one field that is not null here, and it is not an
 * override: it is the scope a target of this package starts with, and it is a real decision
 * rather than a missing one. Adding Instagram means "stop me watching Reels", so a new
 * Instagram row is born [BlockScope.SHORTS_ONLY]; every app without a short-form surface is
 * born [BlockScope.WHOLE_APP], which is also what an inserted row has always meant. Because
 * this entity is only ever inserted where there was nothing, it cannot overwrite a scope the
 * user has since chosen — `TargetDao.insertIfAbsent` ignores a row that already exists.
 */
fun newTarget(packageName: String, label: String, nowMillis: Long): TargetEntity =
    TargetEntity(
        packageName = packageName,
        label = label,
        enabled = true,
        createdAtMillis = nowMillis,
        blockScope = ShortsCatalog.defaultScopeFor(packageName).stored,
    )

/** Pure mapping from the enumeration to the added-only Targets state. */
fun targetsUiState(list: TargetsListState): TargetsViewState = when (list) {
    TargetsListState.Loading -> TargetsViewState.Loading

    // No list to filter and no added set to claim — either would be a guess.
    TargetsListState.Failed -> TargetsViewState.LoadFailed

    is TargetsListState.Loaded -> {
        val added = addedTargets(list.picks)
        when {
            added.isNotEmpty() -> TargetsViewState.Rows(added)
            // Nothing installed outranks "nothing added": ADD would open an empty picker.
            list.picks.isEmpty() -> TargetsViewState.NoLaunchableApps
            else -> TargetsViewState.Empty
        }
    }
}

/** Pure mapping from the enumeration plus the normalized query to the picker's state. */
fun pickerUiState(
    list: TargetsListState,
    normalizedQuery: String,
): TargetPickerViewState = when (list) {
    TargetsListState.Loading -> TargetPickerViewState.Loading
    TargetsListState.Failed -> TargetPickerViewState.LoadFailed
    is TargetsListState.Loaded -> {
        val filtered = filterLaunchable(list.picks, normalizedQuery)
        when {
            list.picks.isEmpty() -> TargetPickerViewState.NoLaunchableApps
            filtered.isNotEmpty() -> TargetPickerViewState.Rows(filtered)
            else -> TargetPickerViewState.NoResults
        }
    }
}

/**
 * Combines the enumeration with the search text so the filter runs only when the picks
 * or the normalized query actually change — a trailing space in the field, for
 * instance, neither re-filters nor re-emits.
 */
fun pickerUiFlow(
    list: Flow<TargetsListState>,
    rawQuery: Flow<String>,
): Flow<TargetPickerViewState> = combine(
    list,
    rawQuery.map(::normalizeTargetsQuery).distinctUntilChanged(),
) { state, normalizedQuery ->
    pickerUiState(state, normalizedQuery)
}
