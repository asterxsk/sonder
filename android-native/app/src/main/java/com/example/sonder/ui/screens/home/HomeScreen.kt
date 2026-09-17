package com.example.sonder.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.PixelTab
import com.example.sonder.ui.kit.BadgeTone
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.PixelTimer
import com.example.sonder.ui.kit.TimerTone

/**
 * Home: the console view of live enforcement state (§14 badges), then the limited
 * apps. The shared dock lives in SonderRoot's PixelAppScaffold, so this only draws
 * into the padding it is handed — no insets and no dock of its own.
 */
@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onSelectTab: (PixelTab) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                androidx.compose.material3.Text(
                    text = "SONDER",
                    style = PixelTypeScale.Wordmark,
                    fontFamily = PixelFont,
                    color = PixelPalette.Primary,
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = "limits are earned back one hand at a time",
                    style = MonoTypeScale.Metadata,
                    color = PixelPalette.Muted,
                )
            }
        }

        // Everything is one lazy list, so a short screen or a large font scale can
        // scroll to the panel's action instead of clipping it off the bottom.
        item {
            if (current == null) {
                HomeLoadingPanel()
            } else {
                HomeStatusPanel(
                    summary = current.summary,
                    onLimitApps = { onSelectTab(PixelTab.TARGETS) },
                )
            }
        }

        if (current != null && current.rows.isNotEmpty()) {
            item {
                androidx.compose.material3.Text(
                    text = "LIMITED APPS",
                    style = PixelTypeScale.SectionTitle,
                    fontFamily = PixelFont,
                    color = PixelPalette.Muted,
                )
            }
            items(current.rows, key = { it.packageName }) { row -> HomeTargetPanel(row) }
        }
    }
}

/** Loading: restrained pixel progress so an initializing screen never reads as frozen. */
@Composable
private fun HomeLoadingPanel() {
    val transition = rememberInfiniteTransition(label = "homeLoading")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = LinearEasing)),
        label = "phase",
    )

    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Text(
                text = "READING PROTECTION STATE",
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (index < phase) PixelPalette.Primary else PixelPalette.BorderDark,
                            ),
                    )
                }
            }
        }
    }
}

/** The compact live-status panel: one deterministic presentation per [HomeSummary]. */
@Composable
private fun HomeStatusPanel(summary: HomeSummary, onLimitApps: () -> Unit) {
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (summary) {
                HomeSummary.NoTargets -> {
                    androidx.compose.material3.Text(
                        text = "NO APPS LIMITED",
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Text(
                        text = "Pick the apps you want to gate\nbehind blackjack.",
                        style = MonoTypeScale.Body,
                        color = PixelPalette.Muted,
                    )
                    Spacer(Modifier.height(12.dp))
                    PixelButton(text = "LIMIT APPS", onClick = onLimitApps)
                }

                is HomeSummary.Idle -> {
                    androidx.compose.material3.Text(
                        text = "PROTECTION READY",
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Text(
                        text = limitedCountLabel(summary.enabledCount),
                        style = MonoTypeScale.Body,
                        color = PixelPalette.Primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.material3.Text(
                        text = "Opening one requires winning a hand of blackjack.",
                        style = MonoTypeScale.Metadata,
                        color = PixelPalette.Muted,
                    )
                }

                is HomeSummary.Active -> {
                    val locked = summary.row.state == EnforcementState.LOCKED
                    androidx.compose.material3.Text(
                        text = summary.row.label,
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                    Spacer(Modifier.height(8.dp))
                    PixelStatusBadge(if (locked) BadgeTone.LOCKED else BadgeTone.GRANTED)
                    Spacer(Modifier.height(12.dp))
                    PixelTimer(
                        timeText = summary.row.remainingText,
                        caption = if (locked) "LOCKED FOR" else "ACCESS LEFT",
                        tone = if (locked) TimerTone.LOCKED else TimerTone.GRANTED,
                    )
                }
            }
        }
    }
}

/** One limited app: label, its live state badge, and the package id underneath. */
@Composable
private fun HomeTargetPanel(row: HomeRow) {
    val stateWord = row.stateWord()
    val description = listOf(row.label, stateWord, row.remainingText)
        .filter { it.isNotEmpty() }
        .joinToString(", ")

    PixelPanel(
        modifier = Modifier
            .fillMaxWidth()
            // One merged node per row: the badge glyph never gets spelled out, and the
            // state reads as a word next to the app name.
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.Text(
                    text = row.label,
                    style = PixelTypeScale.SectionTitle,
                    fontFamily = PixelFont,
                    color = PixelPalette.Text,
                )
                when (row.state) {
                    EnforcementState.GRANTED ->
                        PixelStatusBadge(BadgeTone.GRANTED, labelOverride = row.remainingText)
                    EnforcementState.LOCKED ->
                        PixelStatusBadge(BadgeTone.LOCKED, labelOverride = row.remainingText)
                    else ->
                        PixelStatusBadge(BadgeTone.PLAYING)
                }
            }
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.Text(
                text = row.packageName,
                style = MonoTypeScale.PackageId,
                color = PixelPalette.Muted,
            )
        }
    }
}

/** "1 APP LIMITED" / "3 APPS LIMITED" — the panel's live enabled count. */
private fun limitedCountLabel(count: Int): String =
    if (count == 1) "1 APP LIMITED" else "$count APPS LIMITED"

/** Readable state word for the row's accessibility description. */
private fun HomeRow.stateWord(): String = when (state) {
    EnforcementState.LOCKED -> "LOCKED"
    EnforcementState.GRANTED -> "ACCESS GRANTED"
    EnforcementState.IDLE -> "IDLE"
    EnforcementState.DISABLED -> "OFF"
}
