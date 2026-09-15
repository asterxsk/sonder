package com.example.sonder.ui.screens.onboarding

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sonder.platform.permissions.SonderPermission
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.PixelButton

/**
 * First-launch wizard (plan §4): each permission explained in pixel-dialogue copy,
 * deep-linked to its settings page. Stays until everything is granted.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val missing by viewModel.missing.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Text(
            text = "SONDER",
            style = PixelTypeScale.Wordmark,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            text = "Set your limits. Play to break them.",
            style = MonoTypeScale.Body,
            color = PixelPalette.Muted,
        )
        Spacer(Modifier.height(24.dp))

        Step(
            number = "01",
            title = "ACCESSIBILITY",
            body = "Lets Sonder see which app you just opened — nothing more. No screen content is ever read.",
            granted = SonderPermission.ACCESSIBILITY !in missing,
            onOpen = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    putExtra(
                        Intent.EXTRA_COMPONENT_NAME,
                        ComponentName(context, "com.example.sonder.platform.accessibility.SonderAccessibilityService"),
                    )
                }
                runCatching { context.startActivity(intent) }
            },
        )
        Spacer(Modifier.height(12.dp))

        Step(
            number = "02",
            title = "DISPLAY OVER OTHER APPS",
            body = "Shows the block screen the instant a limited app opens, before the table loads.",
            granted = SonderPermission.OVERLAY !in missing,
            onOpen = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))

        Step(
            number = "03",
            title = "USAGE ACCESS",
            body = "Powers the app picker and your usage stats. Stays on the device.",
            granted = SonderPermission.USAGE_ACCESS !in missing,
            onOpen = {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            },
        )
        Spacer(Modifier.height(12.dp))

        Step(
            number = "04",
            title = "NOTIFICATIONS",
            body = "Tells you when access expires or a lockout ends.",
            granted = SonderPermission.NOTIFICATIONS !in missing,
            onOpen = {
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        },
                    )
                }
            },
        )

        Spacer(Modifier.height(24.dp))
        if (missing.isEmpty()) {
            PixelButton(
                text = "BEGIN ✓",
                onClick = { viewModel.completeOnboarding(onDone) },
            )
        } else {
            androidx.compose.material3.Text(
                text = "Grant all four to continue.",
                style = MonoTypeScale.Metadata,
                color = PixelPalette.Muted,
            )
            Spacer(Modifier.height(8.dp))
            PixelButton(
                text = "RE-CHECK",
                style = com.example.sonder.ui.kit.PixelButtonStyle.SECONDARY,
                onClick = { viewModel.refresh() },
            )
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun Step(
    number: String,
    title: String,
    body: String,
    granted: Boolean,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PixelPalette.Surface)
            .border(2.dp, if (granted) PixelPalette.Success else PixelPalette.BorderDark)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Text(
                    text = number,
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = PixelPalette.Muted,
                )
                androidx.compose.material3.Text(
                    text = title,
                    style = PixelTypeScale.SectionTitle,
                    fontFamily = PixelFont,
                    color = if (granted) PixelPalette.Success else PixelPalette.Text,
                )
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                text = body,
                style = MonoTypeScale.Metadata,
                color = PixelPalette.Muted,
            )
        }
        Box(Modifier.padding(start = 8.dp)) {
            if (granted) {
                androidx.compose.material3.Text("✓", color = PixelPalette.Success)
            } else {
                PixelButton(text = "OPEN", onClick = onOpen, minHeight = 40.dp)
            }
        }
    }
}
