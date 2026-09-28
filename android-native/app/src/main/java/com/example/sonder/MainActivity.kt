package com.example.sonder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.example.sonder.data.settings.SettingsRepository
import com.example.sonder.platform.permissions.PermissionAudit
import com.example.sonder.platform.permissions.PermissionPromptActivity
import com.example.sonder.theme.SonderTheme
import com.example.sonder.theme.enablePixelEdgeToEdge
import com.example.sonder.ui.SonderRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var audit: PermissionAudit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light system icons over the transparent near-black bars — the auto-detected
        // default draws dark icons on this dark app, where they vanish.
        enablePixelEdgeToEdge()

        // User rule: once the app is sure a permission is missing, pop the pixel
        // reminder 10 seconds after open (accessibility state settles by then).
        //
        // Skipped entirely until onboarding is finished. The wizard is already walking
        // the user through these exact four grants one at a time; a second dialog on top
        // of it, built from its own snapshot, is what made people grant the same
        // permission twice. Also skipped if the app was backgrounded before the timer
        // fired — launching an activity from the background is blocked anyway, and a
        // prompt that appears out of nowhere later is worse than no prompt.
        // savedInstanceState: only on a genuine launch. Without it a rotation would
        // restart the timer and re-pop a dialog the user was already reading.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                delay(PermissionAudit.AUDIT_DELAY_MILLIS)
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@launch
                if (!settings.isOnboardingDone.first()) return@launch
                PermissionPromptActivity.launchIfMissing(this@MainActivity, audit)
            }
        }

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
