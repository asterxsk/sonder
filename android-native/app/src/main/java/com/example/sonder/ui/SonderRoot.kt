package com.example.sonder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.sonder.Main
import com.example.sonder.Onboarding
import com.example.sonder.Settings
import com.example.sonder.Stats
import com.example.sonder.Targets
import com.example.sonder.theme.PixelPalette
import com.example.sonder.ui.kit.PixelAppScaffold
import com.example.sonder.ui.screens.home.HomeScreen
import com.example.sonder.ui.screens.onboarding.OnboardingScreen
import com.example.sonder.ui.screens.settings.SettingsScreen
import com.example.sonder.ui.screens.stats.StatsScreen
import com.example.sonder.ui.screens.targets.TargetsScreen

/**
 * Root navigation host. Blocks on the onboarding decision (null = still loading).
 * Nav3 backstack with serializable keys; the one PixelAppScaffold here means the
 * pixel dock exists exactly once across all four management screens.
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

            // NavPolicy decides the stack shape; this only applies the result. It can
            // never empty the stack — an empty back stack renders nothing and looks
            // like a freeze. System back, the dock, and Home's LIMIT APPS action all
            // funnel through here.
            val applyBackStack: (List<NavKey>) -> Unit = { next ->
                if (next != backStack.toList()) {
                    backStack.clear()
                    backStack.addAll(next)
                }
            }

            PixelAppScaffold(
                selectedTab = NavPolicy.tabOf(backStack),
                onSelectTab = { tab -> applyBackStack(NavPolicy.select(backStack, tab)) },
            ) { contentPadding ->
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { applyBackStack(NavPolicy.back(backStack)) },
                    entryProvider = entryProvider {
                        entry<Main> {
                            HomeScreen(
                                contentPadding = contentPadding,
                                onSelectTab = { tab ->
                                    applyBackStack(NavPolicy.select(backStack, tab))
                                },
                            )
                        }
                        entry<Targets> { TargetsScreen(contentPadding = contentPadding) }
                        entry<Stats> { StatsScreen(contentPadding = contentPadding) }
                        entry<Settings> { SettingsScreen(contentPadding = contentPadding) }
                    },
                )
            }
        }
    }
}
