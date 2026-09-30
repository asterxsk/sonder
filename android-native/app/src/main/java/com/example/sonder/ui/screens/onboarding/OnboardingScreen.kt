package com.example.sonder.ui.screens.onboarding

import androidx.activity.compose.LocalActivity
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.sonder.platform.permissions.PermissionHandoff
import com.example.sonder.platform.permissions.SonderPermission
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import kotlinx.coroutines.delay

/**
 * First-launch wizard (plan §4): one permission per step, deep-linked, with a
 * live progress rail. States auto-refresh every second — the moment you grant
 * in Settings and come back, the step completes and the next one appears instantly.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val skipped by viewModel.skipped.collectAsStateWithLifecycle()
    val unresolved by viewModel.unresolved.collectAsStateWithLifecycle()
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

    // The wizard hands off to Settings and comes back on its own; if it leaves the
    // screen for good, stop watching so a completed grant cannot resurrect it later.
    //
    // A configuration change is not leaving: this screen is disposed and rebuilt, and
    // cancelling here killed the watcher that is supposed to raise the app again when
    // the user returns from Settings — they would come back to a wizard that never
    // noticed the grant. The handoff outlives the composition on purpose.
    val activity = LocalActivity.current
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isChangingConfigurations != true) PermissionHandoff.cancel()
        }
    }

    // Done means nothing outstanding, not nothing missing: an optional permission the user
    // turned down on purpose is not the wizard still waiting on them.
    val allGranted = unresolved.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            // Full-screen flow outside the nav scaffold, so it owns its own insets:
            // the wizard must never slide under the status bar or the nav bar.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PixelSpace.Room),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(PixelSpace.Edge))
        androidx.compose.material3.Text(
            text = "SONDER",
            style = PixelTypeScale.Wordmark,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(PixelSpace.Tight))
        androidx.compose.material3.Text(
            text = "Set your limits. Play to break them.",
            style = MonoTypeScale.Body,
            color = TextSoft,
        )
        Spacer(Modifier.height(PixelSpace.Section))

        // Progress rail: one block per step, filled up to where the user is. It is
        // positional rather than per-permission: the label under it says "STEP 2 OF 4",
        // and a rail that lit a later block green because that permission happened to be
        // granted already would contradict the number right beneath it. So the current
        // step is green, everything past it is the unfilled tone, and the rail only
        // reaches its end when the last step is done.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PixelSpace.Tight),
        ) {
            SonderPermission.entries.forEachIndexed { index, _ ->
                val reached = allGranted || index <= currentStep
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ProgressRailHeight)
                        .background(if (reached) PixelPalette.Success else PixelPalette.Panel)
                        .border(
                            PixelSpace.Stroke,
                            if (reached) PixelPalette.Success else PixelPalette.BorderDark,
                        ),
                )
            }
        }
        Spacer(Modifier.height(PixelSpace.Tight))
        androidx.compose.material3.Text(
            text = when {
                // The claim has to stay literally true: with an optional permission declined
                // the wizard is finished, but not everything is granted, and saying so on
                // the last screen is the difference between a choice and a lie.
                !allGranted -> "STEP ${currentStep + 1} OF $total"
                missing.isEmpty() -> "ALL PERMISSIONS GRANTED ✓"
                else -> "SETUP COMPLETE — ${skipped.size} OPTIONAL OFF"
            },
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = if (allGranted) PixelPalette.Success else PixelPalette.Muted,
        )
        Spacer(Modifier.height(PixelSpace.Section))

        if (allGranted) {
            CompletionCard(onBegin = { viewModel.completeOnboarding(onDone) })
        } else {
            val p = SonderPermission.entries[currentStep.coerceAtMost(total - 1)]
            StepCard(
                permission = p,
                stepNumber = currentStep + 1,
                totalSteps = total,
                alreadyGranted = false,
                onOpen = { PermissionHandoff.request(context, p) },
                // Only an optional step can be walked past; the other three are the
                // enforcement itself and the wizard has nothing to offer without them.
                onSkip = if (p.optional) ({ viewModel.skip(p) }) else null,
            )
            Spacer(Modifier.height(PixelSpace.Base))

            // Future steps shown ghosted so the user sees the road ahead.
            SonderPermission.entries.drop(currentStep + 1).forEach { upcoming ->
                GhostStep(permission = upcoming)
                Spacer(Modifier.height(PixelSpace.Snug))
            }
        }
        // Bottom clearance so the last control is never flush to the gesture area.
        Spacer(Modifier.height(PixelSpace.Target))
    }
}

/** One block of the progress rail; taller than a stroke so the fill reads as a segment. */
private val ProgressRailHeight = 8.dp

@Composable
private fun StepCard(
    permission: SonderPermission,
    stepNumber: Int,
    totalSteps: Int,
    alreadyGranted: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    /** Present only for an optional permission: walks past this step for good. */
    onSkip: (() -> Unit)? = null,
) {
    val copy = copyFor(permission)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PixelPalette.Surface)
            .border(PixelSpace.Stroke, PixelPalette.Primary)
            .padding(PixelSpace.Room),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(PixelPalette.Primary)
                    .border(PixelSpace.Stroke, PixelPalette.PrimaryDark)
                    .padding(horizontal = PixelSpace.Snug, vertical = PixelSpace.Tight),
            ) {
                androidx.compose.material3.Text(
                    "$stepNumber/$totalSteps",
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = PixelPalette.Bg,
                )
            }
            androidx.compose.material3.Text(
                copy.title,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
                modifier = Modifier.padding(start = PixelSpace.Base),
            )
        }
        Spacer(Modifier.height(PixelSpace.Base))
        androidx.compose.material3.Text(
            copy.body,
            style = MonoTypeScale.Body,
            color = PixelPalette.Text,
        )
        Spacer(Modifier.height(PixelSpace.Snug))
        androidx.compose.material3.Text(
            copy.reassure,
            style = MonoTypeScale.Metadata,
            color = PixelPalette.Muted,
        )
        Spacer(Modifier.height(PixelSpace.Room))
        PixelButton(
            text = "GRANT  →",
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
        )
        // The opt-out sits under the grant, quieter and un-framed: the default is still to
        // grant, and a skippable step that shouted would be a different app.
        onSkip?.let { skip ->
            Spacer(Modifier.height(PixelSpace.Snug))
            PixelButton(
                text = "SKIP — I'LL WATCH THE TIMER MYSELF",
                onClick = skip,
                style = PixelButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun GhostStep(permission: SonderPermission) {
    val copy = copyFor(permission)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelPalette.Surface)
            .border(PixelSpace.Stroke, PixelPalette.BorderDark)
            .padding(horizontal = PixelSpace.Base, vertical = PixelSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            "□",
            color = PixelPalette.Muted,
            modifier = Modifier.padding(end = PixelSpace.Base),
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
            .border(PixelSpace.Stroke, PixelPalette.Success)
            .padding(PixelSpace.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text(
            "✓",
            style = PixelTypeScale.Timer,
            fontFamily = PixelFont,
            color = PixelPalette.Success,
        )
        Spacer(Modifier.height(PixelSpace.Base))
        androidx.compose.material3.Text(
            "SONDER IS ARMED",
            style = PixelTypeScale.SectionTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Text,
        )
        Spacer(Modifier.height(PixelSpace.Snug))
        androidx.compose.material3.Text(
            "Pick the apps worth the gamble.\nEvery open costs a hand.",
            style = MonoTypeScale.Body,
            color = PixelPalette.Muted,
        )
        Spacer(Modifier.height(PixelSpace.Room))
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
        title = "NOTIFICATIONS — OPTIONAL",
        body = "Sonder tells you when access expires or a lockout ends — otherwise you'd never know why an app is blocked.",
        reassure = "Only enforcement alerts. No marketing, ever. Skip it and blocking still works.",
    )
}
