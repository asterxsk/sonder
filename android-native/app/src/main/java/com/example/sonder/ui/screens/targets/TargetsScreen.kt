package com.example.sonder.ui.screens.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelTabs
import com.example.sonder.ui.kit.TargetRow

/**
 * Targets: picker for the apps gated behind blackjack (§8). The dock, background,
 * and system insets come from the shared [PaddingValues]; there is no Back or DONE —
 * the dock is the only way out, so the list keeps the screen height.
 */
@Composable
fun TargetsScreen(
    contentPadding: PaddingValues,
    viewModel: TargetsViewModel = hiltViewModel(),
) {
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            "TARGETS",
            style = PixelTypeScale.ScreenTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(8.dp))

        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            placeholder = { androidx.compose.material3.Text("search apps…", style = MonoTypeScale.Body, color = PixelPalette.Muted) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MonoTypeScale.Body,
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))

        PixelTabs(
            tabs = listOf("ALL", "LIMITED (${ui.enabledCount})"),
            selected = if (ui.tab == TargetsTab.LIMITED) 1 else 0,
            onSelect = { index -> viewModel.setTab(if (index == 1) TargetsTab.LIMITED else TargetsTab.ALL) },
        )
        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val view = ui.view) {
                TargetsViewState.Loading -> FramedNote(
                    title = "LOADING APPS",
                    body = "Reading what can be launched on this device.",
                    busy = true,
                )
                TargetsViewState.NoLaunchableApps -> FramedNote(
                    title = "NO LAUNCHABLE APPS",
                    body = "This device reports nothing with a launcher to gate.",
                )
                TargetsViewState.LoadFailed -> FramedNote(
                    title = "APPS UNAVAILABLE",
                    body = "The system did not return the app list. This is not an empty device.",
                ) {
                    PixelButton(
                        text = "TRY AGAIN",
                        onClick = viewModel::retry,
                        style = PixelButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TargetsViewState.EmptyLimited -> FramedNote(
                    title = "NOTHING LIMITED",
                    body = "Switch to ALL and turn on an app to gate it behind blackjack.",
                )
                TargetsViewState.NoResults -> FramedNote(
                    title = "NO MATCHES",
                    body = "No app on this tab matches that search.",
                )
                is TargetsViewState.Rows -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(view.picks, key = { it.packageName }) { pick ->
                        TargetRow(
                            appName = pick.label,
                            packageName = pick.packageName,
                            enabled = pick.enabled,
                            iconGlyph = if (pick.enabled) "♠" else "▣",
                            onClick = { viewModel.toggle(pick.packageName, pick.label, !pick.enabled) },
                        )
                    }
                }
            }
        }

        // Outside the scrolling list, above the dock inset, so it never drifts over
        // the rows or hides the last one.
        androidx.compose.material3.Text(
            "ON means opening that app requires winning a hand of blackjack.",
            style = MonoTypeScale.Metadata,
            color = PixelPalette.Muted,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/**
 * Framed explanatory state — a panel, not a blank list, so empty never reads as
 * frozen. [action] is the optional recovery control a failure state needs; the other
 * states pass none.
 */
@Composable
private fun FramedNote(
    title: String,
    body: String,
    busy: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = PixelPalette.Primary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(12.dp))
            }
            androidx.compose.material3.Text(
                title,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                body,
                style = MonoTypeScale.Body,
                color = PixelPalette.Muted,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                action()
            }
        }
    }
}
