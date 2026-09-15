package com.example.sonder.ui.screens.settings

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.sonder.platform.permissions.PermissionAudit
import com.example.sonder.platform.permissions.SonderPermission
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.BadgeTone

/** Settings: permission health with deep links (mirror of onboarding, always reachable). */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val audit = remember { PermissionAudit(context.applicationContext) }
    var missing by remember { mutableStateOf(audit.missingPermissions()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .windowInsetsPadding(WindowInsets.statusBars)
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
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
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
                                minHeight = 40.dp,
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
            onClick = { missing = audit.missingPermissions() },
        )
        Spacer(Modifier.height(16.dp))
        PixelButton(text = "← BACK", onClick = onBack, style = PixelButtonStyle.SECONDARY)
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
