package com.example.sonder.ui.screens.settings

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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.sonder.data.settings.SettingsRepository
import com.example.sonder.platform.permissions.PermissionAudit
import com.example.sonder.platform.permissions.PermissionHandoff
import com.example.sonder.platform.permissions.SonderPermission
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.BadgeTone
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
    val settings = remember(context) { SettingsRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()

    // Which optional permissions the user has turned down. Read here rather than folded
    // into the audit: the audit answers "what does the system say", and this answers "what
    // did the user say", and the row needs both to tell "off because you chose so" from
    // "off, and something is broken".
    val skippedNames by remember(settings) { settings.skippedPermissionNames }
        .collectAsStateWithLifecycle(initialValue = emptySet())

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
            .padding(PixelSpace.Room),
        // Left-aligned, like Home, Targets and Stats. This screen used to centre its
        // heading while every other screen starts flush left, which is the kind of
        // one-screen difference that reads as a mistake rather than as a choice.
    ) {
        androidx.compose.material3.Text(
            "SETTINGS",
            style = PixelTypeScale.ScreenTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(PixelSpace.Room))

        PixelPanel(modifier = Modifier.fillMaxWidth()) {
            Column {
                androidx.compose.material3.Text(
                    "PERMISSIONS",
                    style = PixelTypeScale.SectionTitle,
                    fontFamily = PixelFont,
                    color = PixelPalette.Text,
                )
                Spacer(Modifier.height(PixelSpace.Base))
                SonderPermission.entries.forEach { p ->
                    val granted = p !in missing
                    // An optional permission that is off *and* was turned down is working as
                    // designed; showing it in the failure colour would call the user's own
                    // choice a fault, and the reminder that used to nag about it is gone.
                    val declinedOptional = !granted && p.optional && p.name in skippedNames
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = PixelSpace.Snug),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Text(
                            if (declinedOptional) "${p.label}  (OPTIONAL)" else p.label,
                            style = MonoTypeScale.Body,
                            color = when {
                                granted -> PixelPalette.Text
                                declinedOptional -> TextSoft
                                else -> PixelPalette.Danger
                            },
                        )
                        when {
                            granted -> PixelStatusBadge(BadgeTone.GRANTED, labelOverride = "OK")
                            declinedOptional -> PixelButton(
                                text = "TURN ON",
                                onClick = {
                                    // Wanting it back has to clear the "no" as well as open
                                    // Settings, or the on-open reminder would keep ignoring a
                                    // permission the user has just asked for.
                                    scope.launch { settings.unskipPermission(p) }
                                    PermissionHandoff.request(context, p)
                                },
                                modifier = Modifier.semantics {
                                    contentDescription = "TURN ON ${p.label}"
                                },
                                minHeight = PixelSpace.Target,
                            )
                            else -> PixelButton(
                                text = "FIX",
                                onClick = { PermissionHandoff.request(context, p) },
                                // "FIX" alone reads the same four times in TalkBack; name the row.
                                modifier = Modifier.semantics { contentDescription = "FIX ${p.label}" },
                                minHeight = PixelSpace.Target,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(PixelSpace.Section))
        PixelButton(
            text = "RE-CHECK",
            style = PixelButtonStyle.SECONDARY,
            onClick = { missingState.value = audit.missingPermissions() },
        )
        Spacer(Modifier.height(PixelSpace.Room))
    }
}
