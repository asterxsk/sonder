package com.example.sonder.ui.screens.settings

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.platform.permissions.PermissionAudit
import com.example.sonder.platform.permissions.SonderPermission
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.BadgeTone
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Settings: permission health with deep links (mirror of onboarding, always reachable).
 * The dock, background, and system insets come from the shared [PaddingValues].
 */
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val audit = remember(context) { PermissionAudit(context.applicationContext) }

    // One published screen state. The audit runs on an explicit lifecycle event —
    // ON_RESUME, which includes coming back from Android Settings — and on RE-CHECK.
    // Ordinary recomposition never re-audits.
    val missingState = remember(audit) { MutableStateFlow(audit.missingPermissions()) }
    val missing by missingState.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, audit) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) missingState.value = audit.missingPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            // Scrolls inside the safe area, so large font scales can never push a row
            // under the dock or the system bars.
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.Text(
            "SETTINGS",
            style = PixelTypeScale.ScreenTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        PixelPanel(modifier = Modifier.fillMaxWidth()) {
            Column {
                androidx.compose.material3.Text(
                    "PERMISSIONS",
                    style = PixelTypeScale.SectionTitle,
                    fontFamily = PixelFont,
                    color = PixelPalette.Text,
                )
                Spacer(Modifier.height(12.dp))
                SonderPermission.entries.forEach { p ->
                    val granted = p !in missing
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Text(
                            p.label,
                            style = MonoTypeScale.Body,
                            color = if (granted) PixelPalette.Text else PixelPalette.Danger,
                        )
                        if (granted) {
                            PixelStatusBadge(BadgeTone.GRANTED, labelOverride = "OK")
                        } else {
                            PixelButton(
                                text = "FIX",
                                onClick = { openPermission(context, p); },
                                // "FIX" alone reads the same four times in TalkBack; name the row.
                                modifier = Modifier.semantics { contentDescription = "FIX ${p.label}" },
                                minHeight = 48.dp,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PixelButton(
            text = "RE-CHECK",
            style = PixelButtonStyle.SECONDARY,
            onClick = { missingState.value = audit.missingPermissions() },
        )
        Spacer(Modifier.height(16.dp))
    }
}

private fun openPermission(context: android.content.Context, permission: SonderPermission) {
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
