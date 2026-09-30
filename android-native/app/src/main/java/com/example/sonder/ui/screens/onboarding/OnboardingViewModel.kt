package com.example.sonder.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.settings.SettingsRepository
import com.example.sonder.platform.permissions.PermissionAudit
import com.example.sonder.platform.permissions.SonderPermission
import com.example.sonder.platform.permissions.skippedPermissionsNamed
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

    /** Every permission the system still reports as not granted. */
    val missing: StateFlow<Set<SonderPermission>> = _missing

    /** The optional ones the user has explicitly turned down. */
    private val _skipped = MutableStateFlow<Set<SonderPermission>>(emptySet())
    val skipped: StateFlow<Set<SonderPermission>> = _skipped

    /**
     * Missing *and* not turned down — what the wizard still has to get through.
     *
     * The wizard's progress, its step number and its completion card all read this rather
     * than [missing], because a permission the user declined on purpose is not something
     * the wizard is still waiting on. [missing] stays for the copy that has to be literally
     * true.
     */
    private val _unresolved = MutableStateFlow<Set<SonderPermission>>(emptySet())
    val unresolved: StateFlow<Set<SonderPermission>> = _unresolved

    /** Index of the first outstanding permission (the current wizard step). */
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
        viewModelScope.launch { refresh() }
    }

    suspend fun refresh() {
        // Assigned before the first suspension — [viewModelScope] is Main.immediate, so
        // this still lands on the frame that called it, and only the skip set (which only
        // ever makes the wizard's answer more lenient) arrives a moment later.
        _missing.value = audit.missingPermissions()
        _skipped.value = skippedPermissionsNamed(settings.skippedPermissionNames.first())
        _unresolved.value = _missing.value - _skipped.value
        _currentStep.value = currentStepOf(_unresolved.value)
    }

    /** Turns down an optional permission and moves the wizard on past it. */
    fun skip(permission: SonderPermission) {
        viewModelScope.launch {
            settings.skipPermission(permission)
            refresh()
        }
    }

    /** First outstanding index, or entries.size when nothing is outstanding. */
    private fun currentStepOf(unresolved: Set<SonderPermission>): Int =
        unresolved.minOfOrNull { SonderPermission.entries.indexOf(it) }
            ?: SonderPermission.entries.size // nothing outstanding

    fun completeOnboarding(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardingDone()
            onDone()
        }
    }
}
