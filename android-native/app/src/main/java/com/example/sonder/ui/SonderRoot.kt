package com.example.sonder.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.sonder.AppSettings
import com.example.sonder.Main
import com.example.sonder.Onboarding
import com.example.sonder.Settings
import com.example.sonder.Stats
import com.example.sonder.TargetPicker
import com.example.sonder.Targets
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PanelDeep
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.ui.kit.PixelAppScaffold
import com.example.sonder.ui.kit.PixelLoader
import com.example.sonder.ui.screens.appsettings.AppSettingsScreen
import com.example.sonder.ui.screens.home.HomeScreen
import com.example.sonder.ui.screens.onboarding.OnboardingScreen
import com.example.sonder.ui.screens.settings.SettingsScreen
import com.example.sonder.ui.screens.stats.StatsScreen
import com.example.sonder.ui.screens.targetpicker.TargetPickerScreen
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
            modifier = Modifier.fillMaxSize().background(PanelDeep),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    text = "SONDER",
                    style = com.example.sonder.theme.PixelTypeScale.SectionTitle,
                    fontFamily = com.example.sonder.theme.PixelFont,
                    color = PixelPalette.Primary,
                )
                Spacer(Modifier.height(PixelSpace.Base))
                PixelLoader()
            }
        }

        false -> OnboardingScreen(onDone = { /* SettingsRepository flag already persisted */ })

        else -> {
            val backStack = rememberNavBackStack(Main)

            // Only needed so Back at the root can leave the app; Nav3 does not own the
            // Activity's finish.
            val activity = LocalActivity.current

            // NavPolicy decides the stack shape; this only applies the result. It can
            // never empty the stack — an empty back stack renders nothing and looks
            // like a freeze. System back, the dock, and Home's LIMIT APPS action all
            // funnel through here.
            val applyBackStack: (List<NavKey>) -> Unit = remember(backStack) {
                { next: List<NavKey> ->
                    if (next != backStack.toList()) {
                        backStack.clear()
                        backStack.addAll(next)
                    }
                }
            }

            // Both ways out of the picker drop *this* entry rather than whatever is on
            // top. DONE commits asynchronously and pops when the write lands, so a Back
            // pressed while that write was still in flight would otherwise be answered
            // twice — the second pop deleting Targets and leaving the user on Home.
            val leavePicker: () -> Unit = {
                if (backStack.lastOrNull() is TargetPicker) applyBackStack(NavPolicy.back(backStack))
            }

            PixelAppScaffold(
                selectedTab = NavPolicy.tabOf(backStack),
                onSelectTab = { tab -> applyBackStack(NavPolicy.select(backStack, tab)) },
            ) { contentPadding ->
                // The insets arrive as a fresh PaddingValues on every pass of this lambda,
                // so keying anything on them would rebuild it every time. A state instead
                // lets the entry graph below be built once and still see the current
                // padding: reading `padding.value` inside a composable is a snapshot read,
                // so the screens recompose when the insets actually move.
                val padding = rememberUpdatedState(contentPadding)

                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = {
                        // null is the policy's way of saying there is nowhere left to go.
                        // Applying an unchanged stack instead swallowed the press, and Back
                        // did nothing at all on Home.
                        val next = NavPolicy.backOrLeave(backStack)
                        if (next != null) applyBackStack(next) else activity?.finish()
                    },
                    // Screens slide, they do not dissolve. A crossfade reads as a screen
                    // that has replaced another with no relationship to it; a slide says
                    // which way the user is going and which way Back comes home.
                    //
                    // This is the one animation in the app that is not run through
                    // §18's quantised easing. That easing pins colour and 2dp presses to
                    // four discrete frames, but across a screen's full width the same
                    // four frames land 90dp apart and read as a stutter rather than as a
                    // slide, so the spatial transition stays continuous — and short, at
                    // the same 180ms the rest of the motion uses.
                    transitionSpec = {
                        slideInHorizontally(tween(PixelMotion.StateMillis)) { it } togetherWith
                            slideOutHorizontally(tween(PixelMotion.StateMillis)) { -it }
                    },
                    popTransitionSpec = {
                        slideInHorizontally(tween(PixelMotion.StateMillis)) { -it } togetherWith
                            slideOutHorizontally(tween(PixelMotion.StateMillis)) { it }
                    },
                    // Built once per back-stack identity rather than on every
                    // recomposition: the destinations are the same five every time, and
                    // rebuilding the provider allocated a fresh set of entry lambdas —
                    // each with its own captured padding — on every pass.
                    entryProvider = remember(applyBackStack) {
                        entryProvider {
                            entry<Main> {
                                HomeScreen(
                                    contentPadding = padding.value,
                                    onSelectTab = { tab ->
                                        applyBackStack(NavPolicy.select(backStack, tab))
                                    },
                                )
                            }
                            entry<Targets> {
                                TargetsScreen(
                                    contentPadding = padding.value,
                                    // The detail rides on top of Targets, so Back and the dock
                                    // both land on the list rather than dropping to Home.
                                    onOpenAppSettings = { packageName ->
                                        applyBackStack(backStack.toList() + AppSettings(packageName))
                                    },
                                    onAddApps = {
                                        applyBackStack(backStack.toList() + TargetPicker)
                                    },
                                )
                            }
                            entry<Stats> { StatsScreen(contentPadding = padding.value) }
                            entry<Settings> { SettingsScreen(contentPadding = padding.value) }
                            entry<AppSettings> { key ->
                                AppSettingsScreen(
                                    packageName = key.packageName,
                                    contentPadding = padding.value,
                                    onBack = { applyBackStack(NavPolicy.back(backStack)) },
                                )
                            }
                            entry<TargetPicker> {
                                // DONE commits in the screen before popping; CANCEL and system
                                // Back commit nothing. All three just drop this entry, revealing
                                // the Targets list underneath.
                                TargetPickerScreen(
                                    contentPadding = padding.value,
                                    onDone = leavePicker,
                                    onCancel = leavePicker,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
