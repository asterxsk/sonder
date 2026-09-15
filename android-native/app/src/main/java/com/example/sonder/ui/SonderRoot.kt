package com.example.sonder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.sonder.Main
import com.example.sonder.Onboarding
import com.example.sonder.Settings
import com.example.sonder.Stats
import com.example.sonder.Targets
import com.example.sonder.theme.PixelPalette
import com.example.sonder.ui.screens.home.HomeScreen
import com.example.sonder.ui.screens.onboarding.OnboardingScreen
import com.example.sonder.ui.screens.settings.SettingsScreen
import com.example.sonder.ui.screens.stats.StatsScreen
import com.example.sonder.ui.screens.targets.TargetsScreen

/**
 * Root navigation host. Blocks on the onboarding decision (null = still loading).
 * Nav3 backstack with serializable keys; the pixel dock switches tabs.
 */
@Composable
fun SonderRoot(onboardingComplete: Boolean?) {
    when (onboardingComplete) {
        null -> Box(
            modifier = Modifier.fillMaxSize().background(PixelPalette.Bg),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator(color = PixelPalette.Primary) }

        false -> OnboardingScreen(onDone = { /* SettingsRepository flag already persisted */ })

        else -> {
            val backStack = rememberNavBackStack(Main)
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<Main> {
                        HomeScreen(
                            onOpenTargets = { backStack.add(Targets) },
                            onOpenStats = { backStack.add(Stats) },
                            onOpenSettings = { backStack.add(Settings) },
                        )
                    }
                    entry<Targets> {
                        TargetsScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Stats> {
                        StatsScreen(onBack = { backStack.removeLastOrNull() })
                    }
                    entry<Settings> {
                        SettingsScreen(onBack = { backStack.removeLastOrNull() })
                    }
                },
            )
        }
    }
}
