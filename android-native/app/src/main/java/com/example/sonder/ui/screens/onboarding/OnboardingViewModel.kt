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

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val audit: PermissionAudit,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _missing = MutableStateFlow<Set<SonderPermission>>(emptySet())
    val missing: StateFlow<Set<SonderPermission>> = _missing

    init {
        refresh()
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
