package com.example.sonder

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Onboarding : NavKey
@Serializable data object Main : NavKey
@Serializable data object Targets : NavKey
@Serializable data object Stats : NavKey
@Serializable data object Settings : NavKey

/** Per-app settings, reached from a row's arrow on the Targets list. */
@Serializable data class AppSettings(val packageName: String) : NavKey
