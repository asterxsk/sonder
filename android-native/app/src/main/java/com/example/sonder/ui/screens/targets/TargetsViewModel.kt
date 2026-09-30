package com.example.sonder.ui.screens.targets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.repo.InstalledAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The added targets. This screen no longer owns a tab or a search field — it shows the
 * enabled set only — so the ViewModel keeps the enumeration and the retry. Removal moved
 * to the per-app settings screen, which is the only place a rule can now be changed.
 */
@HiltViewModel
class TargetsViewModel @Inject constructor(
    targetDao: TargetDao,
    appsRepository: InstalledAppsRepository,
) : ViewModel() {

    private val source = TargetsListSource(targetDao, appsRepository, viewModelScope)

    val ui: StateFlow<TargetsViewState> = source.state
        .map(::targetsUiState)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            TargetsViewState.Loading,
        )

    /** Explicit recovery from [TargetsViewState.LoadFailed]; re-enters Loading first. */
    fun retry() {
        source.retry()
    }
}
