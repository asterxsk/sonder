package com.example.sonder.ui.screens.targetpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.repo.InstalledAppsRepository
import com.example.sonder.ui.screens.targets.TargetPickerViewState
import com.example.sonder.ui.screens.targets.TargetsListSource
import com.example.sonder.ui.screens.targets.newTarget
import com.example.sonder.ui.screens.targets.pickerUiFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The add-apps picker. It owns the search field that Targets gave up, and it is
 * add-only: the enumeration marks already-added apps so the screen can render them
 * checked and inert. Removal stays exclusively behind Targets' delayed `✕`, so the
 * picker can never be an instant unblock.
 */
@HiltViewModel
class TargetPickerViewModel @Inject constructor(
    private val targetDao: TargetDao,
    appsRepository: InstalledAppsRepository,
) : ViewModel() {

    private val source = TargetsListSource(targetDao, appsRepository, viewModelScope)

    private val query = MutableStateFlow("")

    /** Packages ticked this session but not yet committed; added rows are inert, so they
     * can never appear here. */
    private val selected = MutableStateFlow<Set<String>>(emptySet())

    /** Raw search text, bound to the field; normalization happens in [pickerUiFlow]. */
    val queryText: StateFlow<String> = query.asStateFlow()

    /** The newly ticked packages, so the screen draws a check on the right rows. */
    val selection: StateFlow<Set<String>> = selected.asStateFlow()

    val ui: StateFlow<TargetPickerViewState> = pickerUiFlow(source.state, query)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TargetPickerViewState.Loading,
        )

    /** Explicit recovery from [TargetPickerViewState.LoadFailed]; re-enters Loading first. */
    fun retry() {
        source.retry()
    }

    /**
     * Every visit starts clean. Nav3 installs no per-destination ViewModel store here, so
     * this ViewModel is scoped to the activity and outlives the screen: without this, a
     * cancelled visit would leave its ticks and its search text waiting the next time
     * ADD was pressed, reading as changes already staged when none were.
     */
    fun reset() {
        selected.value = emptySet()
        query.value = ""
    }

    fun setQuery(value: String) {
        query.value = value
    }

    /** Toggle one not-yet-added row's tick. Added rows are inert and never call this. */
    fun toggleSelection(packageName: String) {
        val current = selected.value
        selected.value = if (packageName in current) current - packageName else current + packageName
    }

    /**
     * DONE: enable every newly ticked package and then hand back to the caller to pop.
     * Two narrow writes per package — insert the row only if there is none, then enable
     * it under its launcher name — rather than one whole-row replacement. A package with
     * a soft-removed row therefore keeps its per-app overrides, which the user requires,
     * and a settings write landing in the same instant cannot be undone here because
     * neither write names an override column.
     *
     * Runs inside viewModelScope and only invokes [onCommitted] once the writes finish,
     * so the pop cannot cancel the writes mid-flight.
     */
    fun commit(onCommitted: () -> Unit) {
        val toAdd = selected.value
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            toAdd.forEach { packageName ->
                // A row materialised by the settings screen before the app was ever
                // enabled carries only the package id, so the launcher label wins; the
                // stored label is the fallback for a package the enumeration has lost.
                val label = source.labelOf(packageName)
                    ?: targetDao.get(packageName)?.label
                    ?: packageName
                targetDao.insertIfAbsent(newTarget(packageName, label, now))
                targetDao.enable(packageName, label)
            }
            onCommitted()
        }
    }
}
