package com.example.sonder.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.db.GrantDao
import com.example.sonder.data.db.LockoutDao
import com.example.sonder.data.db.TargetDao
import com.example.sonder.domain.model.EnforcementState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TargetUi(
    val packageName: String,
    val label: String,
    val state: EnforcementState,
    val remainingText: String,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    targetDao: TargetDao,
    grantDao: GrantDao,
    lockoutDao: LockoutDao,
) : ViewModel() {

    val targets: StateFlow<List<TargetUi>> =
        combine(targetDao.observeAll(), grantDao.observeAll(), lockoutDao.observeAll()) { ts, gs, ls ->
            val now = System.currentTimeMillis()
            ts.map { t ->
                val grant = gs.find { it.packageName == t.packageName }
                val lockout = ls.find { it.packageName == t.packageName }
                val state = com.example.sonder.domain.AccessPolicy.stateFor(
                    packageName = t.packageName,
                    enabled = t.enabled,
                    grant = grant?.let {
                        com.example.sonder.domain.model.GrantSnapshot(it.packageName, it.endAtMillis, it.lastSeenMillis)
                    },
                    lockout = lockout?.let {
                        com.example.sonder.domain.model.LockoutSnapshot(it.packageName, it.untilMillis, 0)
                    },
                    nowMillis = now,
                )
                val remaining = when (state) {
                    EnforcementState.GRANTED -> grant?.endAtMillis?.minus(now)
                    EnforcementState.LOCKED -> lockout?.untilMillis?.minus(now)
                    else -> null
                }
                TargetUi(
                    packageName = t.packageName,
                    label = t.label,
                    state = state,
                    remainingText = remaining?.takeIf { it > 0 }?.let { format(it) } ?: "",
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun format(millis: Long): String {
        val totalSec = millis / 1000
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60)
    }
}
