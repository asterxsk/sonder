package com.example.sonder.ui.screens.targets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.db.TargetEntity
import com.example.sonder.data.repo.InstalledApp
import com.example.sonder.data.repo.InstalledAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TargetPickUi(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
)

@HiltViewModel
class TargetsViewModel @Inject constructor(
    private val targetDao: TargetDao,
    appsRepository: InstalledAppsRepository,
) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>>(emptyList())

    init {
        viewModelScope.launch {
            // Package enumeration is quick but not instant; do it off main.
            installed.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                appsRepository.launchableApps()
            }
        }
    }

    val picks: StateFlow<List<TargetPickUi>> =
        combine(installed, targetDao.observeAll()) { apps, targets ->
            val targetMap = targets.associateBy { it.packageName }
            apps.map { app ->
                TargetPickUi(
                    packageName = app.packageName,
                    label = app.label,
                    enabled = targetMap[app.packageName]?.enabled ?: false,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
