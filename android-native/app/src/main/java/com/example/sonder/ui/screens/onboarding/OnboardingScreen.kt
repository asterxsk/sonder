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
import androidx.compose.foundation.layout.size
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
import com.example.sonder.ui.permissions.copyFor
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.CardPadding
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelCard
import com.example.sonder.ui.kit.PixelCardVariant
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

        // One progress statement, not two. There used to be a segmented rail *and* a
        // "STEP 1 OF 4" label under it, saying the same thing in two visual languages and
        // disagreeing about the details — the rail was per-permission, the label was
        // positional, so a permission granted out of order lit a block the number denied.
        // The step count is the honest one, and the pips beside it are that count drawn:
        // four marks, one per step, the current one filled.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SonderPermission.entries.forEachIndexed { index, _ ->
                val reached = allGranted || index <= currentStep
                Box(
                    modifier = Modifier
                        .padding(horizontal = PixelSpace.Tight / 2)
                        .size(ProgressPipSize)
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
            style = MonoTypeScale.Metadata,
            color = if (allGranted) PixelPalette.Success else TextSoft,
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

/** One progress pip: a square, because the world has no circles. */
private val ProgressPipSize = 10.dp

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
    // The step you are on is the loudest card in the wizard, which is the one place an
    // accent frame is doing structural work: it says *this* step, where the ghosts below
    // are the steps that are not happening yet.
    PixelCard(
        modifier = modifier,
        variant = PixelCardVariant.SOLID,
        padding = CardPadding.MD,
        intent = PixelPalette.Primary,
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
    // A subtle card, not a solid one: these are the steps that are not happening yet, and
    // giving them the same surface and shadow as the live step flattens the difference the
    // wizard is trying to draw.
    PixelCard(
        variant = PixelCardVariant.SUBTLE,
        padding = CardPadding.SM,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
}

@Composable
private fun CompletionCard(onBegin: () -> Unit) {
    PixelCard(
        variant = PixelCardVariant.SOLID,
        padding = CardPadding.LG,
        intent = PixelPalette.Success,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
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
            "Pick the apps worth the gamble.\nTime you win is yours to spend.",
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
}

// The permission copy — the title, the paragraph, the privacy note and the one-line version
// the on-open reminder shows — lives in ui/permissions/PermissionCopy.kt, because both
// surfaces ask for the same grant and must not describe it in two different voices.
