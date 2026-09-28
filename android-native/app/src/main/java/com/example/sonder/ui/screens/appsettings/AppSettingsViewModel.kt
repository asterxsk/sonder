package com.example.sonder.ui.screens.appsettings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.data.repo.InstalledAppsRepository
import com.example.sonder.domain.AccessRules
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One published state for the per-app settings screen. The package is bound by the
 * screen once (the destination carries it as an argument), then rules and today's
 * grant stream from the repository so a hand played elsewhere is reflected live.
 */
data class AppSettingsUiState(
    val packageName: String = "",
    /** null until the launcher enumeration resolves it; the header falls back to the id. */
    val label: String? = null,
    val rules: AccessRules = AccessRules(),
    /**
     * The raw stored overrides behind [rules]. The screen needs these — not the effective
     * values — to know which knobs are chosen and which inherit, since an effective value
     * equal to the default is ambiguous.
     */
    val overrides: EnforcementRepository.TargetOverrides = EnforcementRepository.TargetOverrides(),
    val grantedTodayMillis: Long = 0L,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val appsRepository: InstalledAppsRepository,
    private val enforcement: EnforcementRepository,
) : ViewModel() {

    private val packageName = MutableStateFlow<String?>(null)
    private val label = MutableStateFlow<String?>(null)

    /** The in-flight label load, so a re-bind supersedes it instead of racing it. */
    private var labelJob: Job? = null

    // Serialises the read-modify-write in [update], so two quick taps on different knobs
    // cannot both read the old rules and lose one of the writes.
    private val writeMutex = Mutex()

    val ui: StateFlow<AppSettingsUiState> =
        combine(
            packageName,
            label,
            packageName.flatMapLatest { name ->
                if (name == null) flowOf(AccessRules()) else enforcement.observeRules(name)
            },
            packageName.flatMapLatest { name ->
                if (name == null) flowOf(0L) else enforcement.observeDailyGrantedMillis(name)
            },
            packageName.flatMapLatest { name ->
                if (name == null) {
                    flowOf(EnforcementRepository.TargetOverrides())
                } else {
                    enforcement.observeOverrides(name)
                        .map { it ?: EnforcementRepository.TargetOverrides() }
                }
            },
        ) { name, resolvedLabel, rules, granted, overrides ->
            AppSettingsUiState(
                packageName = name.orEmpty(),
                label = resolvedLabel,
                rules = rules,
                overrides = overrides,
                grantedTodayMillis = granted,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            AppSettingsUiState(),
        )

    /** Bind the destination's package. Idempotent, so recomposition cannot re-trigger it. */
    fun bind(packageName: String) {
        if (this.packageName.value == packageName) return
        this.packageName.value = packageName
        label.value = null
        labelJob?.cancel()
        labelJob = viewModelScope.launch {
            // The label is display-only; PackageManager is binder traffic and can throw,
            // so a failure falls back to the package id rather than blanking the header.
            // labelFor reads the name directly — no icon rasterization for the whole device.
            val resolved = try {
                appsRepository.labelFor(packageName)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            label.value = resolved ?: packageName
        }
    }

    fun setWinGrant(millis: Long) = update { it.copy(winGrantMillis = millis) }
    fun setLossDebt(millis: Long) = update { it.copy(lossDebtMillis = millis) }
    fun setDebtCeiling(millis: Long) = update { it.copy(maxDebtMillis = millis) }
    fun setAbsenceRevoke(millis: Long) = update { it.copy(absenceRevokeMillis = millis) }
    fun setDailyCap(millis: Long?) = update { it.copy(dailyCapMillis = millis) }

    // Read-modify-write over the raw overrides, not the effective rules: the transform sets
    // one knob and must leave the others' absence intact, so an untouched knob stays DEFAULT.
    private fun update(
        transform: (EnforcementRepository.TargetOverrides) -> EnforcementRepository.TargetOverrides,
    ) {
        val pkg = packageName.value ?: return
        viewModelScope.launch {
            writeMutex.withLock {
                val current = enforcement.overridesFor(pkg)
                enforcement.updateOverrides(pkg, transform(current))
            }
        }
    }
}
