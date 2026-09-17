package com.example.sonder.ui.screens.targets

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.db.TargetEntity
import com.example.sonder.data.repo.InstalledApp
import com.example.sonder.data.repo.InstalledAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "TargetsViewModel"

data class TargetPickUi(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
)

@HiltViewModel
class TargetsViewModel @Inject constructor(
    private val targetDao: TargetDao,
    private val appsRepository: InstalledAppsRepository,
) : ViewModel() {

    /** null until the launcher enumeration finishes — the only slow step here. */
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)

    /** True when the last enumeration threw; separate from null, which only means "not yet". */
    private val loadFailed = MutableStateFlow(false)

    /** The in-flight enumeration, so a retry supersedes it instead of racing it. */
    private var loadJob: Job? = null

    private val tab = MutableStateFlow(TargetsTab.ALL)
    private val query = MutableStateFlow("")

    /** Raw search text, bound to the field; normalization happens in [targetsUiFlow]. */
    val queryText: StateFlow<String> = query.asStateFlow()

    // Room emits on any target change, so toggles show up live. distinctUntilChanged
    // drops an emission that leaves the picks identical.
    private val listState: Flow<TargetsListState> =
        combine(installed, loadFailed, targetDao.observeAll()) { apps, failed, targets ->
            when {
                failed -> TargetsListState.Failed
                apps == null -> TargetsListState.Loading
                else -> TargetsListState.Loaded(
                    mergeTargetPicks(apps, targets.associate { it.packageName to it.enabled }),
                )
            }
        }.distinctUntilChanged()

    val ui: StateFlow<TargetsUiState> =
        targetsUiFlow(listState, tab, query)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                targetsUiState(TargetsListState.Loading, TargetsTab.ALL, ""),
            )

    init {
        loadInstalledApps()
    }

    /** Explicit recovery from [TargetsViewState.LoadFailed]; re-enters Loading first. */
    fun retry() {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        // Supersede any in-flight enumeration. Without this a late failure from the
        // replaced load could set loadFailed after a newer load already succeeded,
        // leaving the screen on APPS UNAVAILABLE with nothing left to clear it. The
        // catch below rethrows CancellationException, so cancelling never sets it.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            installed.value = null
            loadFailed.value = false
            try {
                // The repository enumerates on Dispatchers.IO; nothing is cached here or
                // there, so a fresh visit sees apps installed or removed since last time.
                installed.value = appsRepository.launchableApps()
            } catch (e: Exception) {
                // PackageManager is binder traffic and can throw (DeadObjectException,
                // RemoteException, SecurityException). Without this the throw would escape
                // viewModelScope and take the process down; instead the screen reports it.
                if (e is CancellationException) throw e
                Log.e(TAG, "launcher enumeration failed", e)
                loadFailed.value = true
            }
        }
    }

    fun setTab(value: TargetsTab) {
        tab.value = value
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun toggle(packageName: String, label: String, enabled: Boolean) {
        viewModelScope.launch {
            targetDao.upsert(
                TargetEntity(
                    packageName = packageName,
                    label = label,
                    enabled = enabled,
                    createdAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }
}
