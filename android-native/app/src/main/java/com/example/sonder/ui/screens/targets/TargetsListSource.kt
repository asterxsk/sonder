package com.example.sonder.ui.screens.targets

import android.graphics.Bitmap
import android.util.Log
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.repo.InstalledApp
import com.example.sonder.data.repo.InstalledAppsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val TAG = "TargetsListSource"

/** One launchable app as the picker and the Targets list both read it. */
data class TargetPickUi(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    val icon: Bitmap? = null,
)

/**
 * The launcher enumeration both targets screens read: Targets the list, the picker the
 * catalogue. It exists so the "enumerate once per visit, never cache, and never mistake a
 * failed read for an empty device" rules are written once — the two screens differ only
 * in how they map this state, not in how they obtain it.
 *
 * [scope] is the owning ViewModel's, so a screen's load dies with its ViewModel.
 */
class TargetsListSource(
    private val targetDao: TargetDao,
    private val appsRepository: InstalledAppsRepository,
    private val scope: CoroutineScope,
) {

    /** null until the launcher enumeration finishes — the only slow step here. */
    private val installed = MutableStateFlow<List<InstalledApp>?>(null)

    /** True when the last enumeration threw; separate from null, which only means "not yet". */
    private val loadFailed = MutableStateFlow(false)

    /** The in-flight enumeration, so a retry supersedes it instead of racing it. */
    private var loadJob: Job? = null

    // Room emits on any target change, so an add or a removal shows up live.
    // distinctUntilChanged drops an emission that leaves the picks identical.
    val state: Flow<TargetsListState> =
        combine(installed, loadFailed, targetDao.observeAll()) { apps, failed, targets ->
            when {
                failed -> TargetsListState.Failed
                apps == null -> TargetsListState.Loading
                else -> {
                    // mergeTargetPicks owns label and enabled only; the icon is attached
                    // here by package name, where apps are already distinct by package.
                    val iconsByPackage = apps.associate { it.packageName to it.icon }
                    TargetsListState.Loaded(
                        mergeTargetPicks(apps, targets.associate { it.packageName to it.enabled })
                            .map { pick -> pick.copy(icon = iconsByPackage[pick.packageName]) },
                    )
                }
            }
        }.distinctUntilChanged()

    init {
        load()
    }

    /** Explicit recovery from [TargetsListState.Failed]; re-enters Loading first. */
    fun retry() {
        load()
    }

    /**
     * The launcher's label for [packageName] from the current enumeration, or null if the
     * enumeration has not finished. Every row the picker offers comes from that list, so a
     * package the user ticked is in it — this only guards the race with a reload.
     */
    fun labelOf(packageName: String): String? =
        installed.value?.firstOrNull { it.packageName == packageName }?.label

    private fun load() {
        // Supersede any in-flight enumeration. Without this a late failure from the
        // replaced load could set loadFailed after a newer load already succeeded,
        // leaving the screen on APPS UNAVAILABLE with nothing left to clear it. The
        // catch below rethrows CancellationException, so cancelling never sets it.
        loadJob?.cancel()
        loadJob = scope.launch {
            installed.value = null
            loadFailed.value = false
            try {
                // The repository enumerates on Dispatchers.IO; nothing is cached here or
                // there, so a fresh visit sees apps installed or removed since last time.
                installed.value = appsRepository.launchableApps()
            } catch (e: Exception) {
                // PackageManager is binder traffic and can throw (DeadObjectException,
                // RemoteException, SecurityException). Without this the throw would escape
                // the scope and take the process down; instead the screen reports it.
                if (e is CancellationException) throw e
                Log.e(TAG, "launcher enumeration failed", e)
                loadFailed.value = true
            }
        }
    }
}
