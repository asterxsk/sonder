package com.example.sonder.ui.screens.onboarding

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonder.platform.permissions.SonderPermission
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import kotlinx.coroutines.delay

/**
 * First-launch wizard (plan §4): one permission per step, deep-linked, with a
 * live progress rail. States auto-refresh every second — the moment you grant
 * in Settings and come back, the step completes and the next one slides in.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val currentStep by viewModel.currentStep.collectAsStateWithLifecycle()
    val total = viewModel.totalSteps
    val context = LocalContext.current

    // Poll only while this screen is lifecycle-active. The wizard renders outside
    // the nav graph, so the ViewModel is never cleared — scoping the loop here is
    // what makes the work actually stop when the wizard leaves the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                viewModel.refresh()
                delay(1_000)
            }
        }
    }

    val allGranted = missing.isEmpty()
    val stepAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 180),
        label = "stepAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            // Full-screen flow outside the nav scaffold, so it owns its own insets:
            // the wizard must never slide under the status bar or the nav bar.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        androidx.compose.material3.Text(
            text = "SONDER",
            style = PixelTypeScale.Wordmark,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            text = "Set your limits. Play to break them.",
            style = MonoTypeScale.Body,
            color = PixelPalette.Muted,
        )
        Spacer(Modifier.height(20.dp))

        // Progress rail: one block per permission, filled when granted.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SonderPermission.entries.forEach { p ->
                val granted = p !in missing
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(if (granted) PixelPalette.Success else PixelPalette.Panel)
                        .border(2.dp, if (granted) PixelPalette.Success else PixelPalette.BorderDark),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            text = if (allGranted) "ALL PERMISSIONS GRANTED ✓"
            else "STEP ${currentStep + 1} OF $total",
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = if (allGranted) PixelPalette.Success else PixelPalette.Muted,
        )
        Spacer(Modifier.height(24.dp))

        if (allGranted) {
            CompletionCard(onBegin = { viewModel.completeOnboarding(onDone) })
        } else {
            val p = SonderPermission.entries[currentStep.coerceAtMost(total - 1)]
            StepCard(
                permission = p,
                stepNumber = currentStep + 1,
                totalSteps = total,
                alreadyGranted = false,
                onOpen = { openPermissionSettings(context, p) },
                modifier = Modifier.graphicsLayer { alpha = stepAlpha },
            )
            Spacer(Modifier.height(12.dp))

            // Future steps shown ghosted so the user sees the road ahead.
            SonderPermission.entries.drop(currentStep + 1).forEach { upcoming ->
                GhostStep(permission = upcoming)
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun StepCard(
    permission: SonderPermission,
    stepNumber: Int,
    totalSteps: Int,
    alreadyGranted: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = copyFor(permission)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PixelPalette.Surface)
            .border(2.dp, PixelPalette.Primary)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(PixelPalette.Primary)
                    .border(2.dp, PixelPalette.PrimaryDark)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                androidx.compose.material3.Text(
                    "$stepNumber/$totalSteps",
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = PixelPalette.Bg,
                )
            }
            Spacer(Modifier.height(0.dp))
            androidx.compose.material3.Text(
                copy.title,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        androidx.compose.material3.Text(
            copy.body,
            style = MonoTypeScale.Body,
            color = PixelPalette.Text,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            copy.reassure,
            style = MonoTypeScale.Metadata,
            color = PixelPalette.Muted,
        )
        Spacer(Modifier.height(20.dp))
        PixelButton(
            text = "GRANT  →",
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GhostStep(permission: SonderPermission) {
    val copy = copyFor(permission)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelPalette.Surface)
            .border(2.dp, PixelPalette.BorderDark)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            "□",
            color = PixelPalette.Muted,
            modifier = Modifier.padding(end = 10.dp),
        )
        Column {
            androidx.compose.material3.Text(
                copy.title,
                style = PixelTypeScale.Badge,
                fontFamily = PixelFont,
                color = PixelPalette.Muted,
            )
            androidx.compose.material3.Text(
                copy.reassure,
                style = MonoTypeScale.Metadata,
                color = PixelPalette.Muted,
            )
        }
    }
}

@Composable
private fun CompletionCard(onBegin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelPalette.Surface)
            .border(2.dp, PixelPalette.Success)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text(
            "✓",
            style = PixelTypeScale.Timer,
            fontFamily = PixelFont,
            color = PixelPalette.Success,
        )
        Spacer(Modifier.height(10.dp))
        androidx.compose.material3.Text(
            "SONDER IS ARMED",
            style = PixelTypeScale.SectionTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Text,
        )
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(
            "Pick the apps worth the gamble.\nEvery open costs a hand.",
            style = MonoTypeScale.Body,
            color = PixelPalette.Muted,
        )
        Spacer(Modifier.height(20.dp))
        PixelButton(
            text = "BEGIN ✓",
            onClick = onBegin,
            style = PixelButtonStyle.SUCCESS,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private data class StepCopy(val title: String, val body: String, val reassure: String)

private fun copyFor(p: SonderPermission): StepCopy = when (p) {
    SonderPermission.ACCESSIBILITY -> StepCopy(
        title = "ACCESSIBILITY",
        body = "Sonder needs to know which app you just opened — that's the whole trigger. It watches window changes only.",
        reassure = "No screen content is ever read. No keystrokes. Ever.",
    )
    SonderPermission.OVERLAY -> StepCopy(
        title = "DISPLAY OVER OTHER APPS",
        body = "The block screen must appear the instant a limited app opens — before the table even loads.",
        reassure = "One full-screen pixel frame, nothing else.",
    )
    SonderPermission.USAGE_ACCESS -> StepCopy(
        title = "USAGE ACCESS",
        body = "Powers the app picker and your usage stats, and acts as a fallback detector.",
        reassure = "Data stays on this device. Nothing is uploaded.",
    )
    SonderPermission.NOTIFICATIONS -> StepCopy(
        title = "NOTIFICATIONS",
        body = "Sonder tells you when access expires or a lockout ends — otherwise you'd never know why an app is blocked.",
        reassure = "Only enforcement alerts. No marketing, ever.",
    )
}

private fun openPermissionSettings(context: android.content.Context, permission: SonderPermission) {
    val intent = when (permission) {
        SonderPermission.ACCESSIBILITY ->
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                putExtra(
                    Intent.EXTRA_COMPONENT_NAME,
                    ComponentName(context, "com.example.sonder.platform.accessibility.SonderAccessibilityService"),
                )
            }
        SonderPermission.OVERLAY ->
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
        SonderPermission.USAGE_ACCESS ->
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        SonderPermission.NOTIFICATIONS ->
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
    }
    runCatching { context.startActivity(intent) }
}
