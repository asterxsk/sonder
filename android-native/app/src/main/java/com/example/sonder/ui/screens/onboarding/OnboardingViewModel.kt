package com.example.sonder.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.settings.SettingsRepository
import com.example.sonder.platform.permissions.PermissionAudit
import com.example.sonder.platform.permissions.SonderPermission
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Onboarding state machine (plan §4): a real wizard. One permission per step,
 * explained in pixel-dialogue copy. The screen drives [refresh] while it is
 * lifecycle-active, so returning from Settings flips the step to granted
 * automatically — no manual RE-CHECK pressing. Nothing polls on its own: this
 * ViewModel is never cleared (the wizard renders outside the nav graph), so a
 * self-scheduled loop would outlive the wizard for the rest of the session.
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
        // One audit up front so the very first frame is truthful. Without it the
        // screen's first composition reads the emptySet() default and paints the
        // all-granted completion card before the lifecycle-scoped loop can land —
        // a tap on BEGIN in that window would finish onboarding with nothing
        // granted. This is a one-shot, not a loop: nothing here reschedules itself.
        refresh()
    }

    fun refresh() {
        _missing.value = audit.missingPermissions()
        _currentStep.value = currentStepOf(_missing.value)
    }

    /** First not-yet-granted index, or entries.size when everything is granted. */
    private fun currentStepOf(missing: Set<SonderPermission>): Int =
        missing.minOfOrNull { SonderPermission.entries.indexOf(it) }
            ?: SonderPermission.entries.size // all granted

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone()
            onDone()
        }
    }
}
