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
 * enabled set only — so the ViewModel keeps the enumeration, the retry, and the removal.
 * The picker that owns the search has its own.
 */
@HiltViewModel
class TargetsViewModel @Inject constructor(
    private val targetDao: TargetDao,
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

    /**
     * Soft-remove a target: clear the enabled flag and nothing else. The row is left in
     * place because the per-app overrides are columns on it, so deleting would destroy
     * the settings the user requires a later ADD to restore. The 30 s wait in the
     * screen gates this call; the ViewModel trusts the caller.
     */
    fun remove(packageName: String) {
        viewModelScope.launch { targetDao.setEnabled(packageName, enabled = false) }
    }
}
