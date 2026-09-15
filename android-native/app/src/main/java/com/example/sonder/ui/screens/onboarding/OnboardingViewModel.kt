package com.example.sonder.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.settings.SettingsRepository
import com.example.sonder.platform.permissions.PermissionAudit
import com.example.sonder.platform.permissions.SonderPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Onboarding state machine (plan §4): a real wizard. One permission per step,
 * explained in pixel-dialogue copy. Permission states are polled every second
 * while the wizard is alive, so returning from Settings flips the step to
 * granted automatically — no manual RE-CHECK pressing.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val audit: PermissionAudit,
    private val settings: SettingsRepository,
) : ViewModel() {

    /** Live permission health, polled so the UI reacts without manual refresh. */
    private val _missing = MutableStateFlow<Set<SonderPermission>>(emptySet())
    val missing: StateFlow<Set<SonderPermission>> = _missing

    /** Index of the first not-yet-granted permission (the current wizard step). */
    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep

    /** Total steps = number of permissions, for "STEP 2 OF 4" copy. */
    val totalSteps: Int = SonderPermission.entries.size

    init {
        // Poll while the wizard is on screen. Cheap checks; stops when cleared.
        viewModelScope.launch {
            while (true) {
                _missing.value = audit.missingPermissions()
                _currentStep.value = _missing.value
                    .minOfOrNull { SonderPermission.entries.indexOf(it) }
                    ?: SonderPermission.entries.size // all granted
                delay(1_000)
            }
        }
    }

    fun refresh() {
        _missing.value = audit.missingPermissions()
    }

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone()
            onDone()
        }
    }
}
