package com.example.sonder

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Onboarding : NavKey
@Serializable data object Main : NavKey
@Serializable data object Targets : NavKey
@Serializable data object Stats : NavKey
@Serializable data object Settings : NavKey
