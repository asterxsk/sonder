package com.example.sonder.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sonder_settings",
    // A preferences file that cannot be decoded would otherwise fail every subsequent read
    // for the life of the install. Falling back to empty means the worst case is one lost
    // flag — onboarding runs again — rather than an app that never gets past its splash.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** App-level settings backed by DataStore. */
class SettingsRepository(private val context: Context) {

    private val onboardingDone = booleanPreferencesKey("onboarding_done")

    /**
     * DataStore throws [IOException] when the file cannot be read, and on some devices when
     * a write failed on the previous launch. This flow is what decides which screen the app
     * shows, so an unhandled throw here does not just lose a read — the collector dies and
     * the user is left on the loading screen for good.
     */
    val isOnboardingDone: Flow<Boolean> = context.dataStore.data
        .catch { failure ->
            if (failure is IOException) emit(emptyPreferences()) else throw failure
        }
        .map { it[onboardingDone] ?: false }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[onboardingDone] = true }
    }
}
