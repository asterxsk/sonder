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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelLoader
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelSearchField
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
    onOpenAppSettings: (String) -> Unit,
    viewModel: TargetsViewModel = hiltViewModel(),
) {
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = PixelSpace.Room),
    ) {
        // The screen's rhythm, top to bottom: title, then the search field that owns it,
        // then the tab row, then the list. The gaps step down together with the grouping
        // — 16 to 8 to 12 — so the title reads as a heading rather than as the first row
        // of a list where every gap was 8.
        Spacer(Modifier.height(PixelSpace.Room))
        androidx.compose.material3.Text(
            "TARGETS",
            style = PixelTypeScale.ScreenTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(PixelSpace.Room))

        PixelSearchField(
            value = query,
            onValueChange = viewModel::setQuery,
        )
        Spacer(Modifier.height(PixelSpace.Snug))

        PixelTabs(
            tabs = listOf("ALL", "LIMITED (${ui.enabledCount})"),
            selected = if (ui.tab == TargetsTab.LIMITED) 1 else 0,
            onSelect = { index -> viewModel.setTab(if (index == 1) TargetsTab.LIMITED else TargetsTab.ALL) },
        )
        Spacer(Modifier.height(PixelSpace.Base))

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
                    verticalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
                ) {
                    items(view.picks, key = { it.packageName }) { pick ->
                        TargetRow(
                            appName = pick.label,
                            packageName = pick.packageName,
                            enabled = pick.enabled,
                            iconGlyph = if (pick.enabled) "♠" else "▣",
                            iconBitmap = pick.icon,
                            onClick = { viewModel.toggle(pick.packageName, pick.label, !pick.enabled) },
                            onOpenSettings = { onOpenAppSettings(pick.packageName) },
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
            color = TextSoft,
            modifier = Modifier.padding(vertical = PixelSpace.Snug),
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
                PixelLoader()
                Spacer(Modifier.height(PixelSpace.Base))
            }
            androidx.compose.material3.Text(
                title,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PixelSpace.Snug))
            androidx.compose.material3.Text(
                body,
                style = MonoTypeScale.Body,
                color = TextSoft,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(PixelSpace.Room))
                action()
            }
        }
    }
}
