package com.example.sonder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.data.settings.SettingsRepository
import com.example.sonder.platform.permissions.PermissionAudit
import com.example.sonder.theme.SonderTheme
import com.example.sonder.ui.SonderRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // User rule: once the app is sure a permission is missing, pop the pixel
        // reminder 10 seconds after open (accessibility state settles by then).
        PermissionAudit.scheduleOnOpenAudit(this)

        setContent {
            SonderTheme {
                // Lifecycle-aware, so the DataStore flow is only collected while the UI
                // is STARTED; finishing onboarding still flips straight to Main.
                val onboarded by settings.isOnboardingDone
                    .collectAsStateWithLifecycle(initialValue = null)
                SonderRoot(onboardingComplete = onboarded)
            }
        }
    }
}
