package com.example.sonder.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sonder_settings")

/** App-level settings backed by DataStore. */
class SettingsRepository(private val context: Context) {

    private val onboardingDone = booleanPreferencesKey("onboarding_done")

    val isOnboardingDone: Flow<Boolean> = context.dataStore.data.map { it[onboardingDone] ?: false }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[onboardingDone] = true }
    }
}
