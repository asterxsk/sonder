package com.example.sonder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
                // Observed continuously so finishing onboarding flips straight to Main.
                val onboarded by settings.isOnboardingDone.collectAsState(initial = null)
                SonderRoot(onboardingComplete = onboarded)
            }
        }
    }
}
