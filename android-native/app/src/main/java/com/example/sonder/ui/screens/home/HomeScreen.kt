package com.example.sonder.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.PixelTab
import com.example.sonder.ui.kit.BadgeTone
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelLoader
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.PixelTimer
import com.example.sonder.ui.kit.TimerTone
import com.example.sonder.ui.kit.pixelShadow
import com.example.sonder.ui.kit.pixelSteppedCorners

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
            .padding(horizontal = PixelSpace.Room),
        contentPadding = PaddingValues(top = PixelSpace.Room, bottom = PixelSpace.Room),
        verticalArrangement = Arrangement.spacedBy(PixelSpace.Base),
    ) {
        item {
            Column {
                Row {
                    androidx.compose.material3.Text(
                        text = "SONDER",
                        style = PixelTypeScale.Wordmark,
                        fontFamily = PixelFont,
                        color = PixelPalette.Primary,
                    )
                    Spacer(Modifier.width(PixelSpace.Snug))
                    // The amber pixel after the wordmark: one block of the brand
                    // accent, the way a console prints its cursor.
                    Box(
                        modifier = Modifier
                            .padding(top = PixelSpace.Snug)
                            .size(CursorBlockSize)
                            .background(PixelPalette.Primary),
                    )
                }
                Spacer(Modifier.height(PixelSpace.Tight))
                androidx.compose.material3.Text(
                    text = "limits are earned back one hand at a time",
                    style = MonoTypeScale.Metadata,
                    color = TextSoft,
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
                    color = TextSoft,
                )
            }
            items(current.rows, key = { it.packageName }) { row -> HomeTargetPanel(row) }
        }
    }
}

/** Loading: the shared pixel loader so an initializing screen never reads as frozen. */
@Composable
private fun HomeLoadingPanel() {
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
            Spacer(Modifier.height(PixelSpace.Snug))
            PixelLoader()
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
                    Spacer(Modifier.height(PixelSpace.Snug))
                    androidx.compose.material3.Text(
                        text = "Pick the apps you want to gate\nbehind blackjack.",
                        style = MonoTypeScale.Body,
                        color = TextSoft,
                    )
                    Spacer(Modifier.height(PixelSpace.Base))
                    PixelButton(text = "LIMIT APPS", onClick = onLimitApps)
                }

                is HomeSummary.Idle -> {
                    androidx.compose.material3.Text(
                        text = "PROTECTION READY",
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                    Spacer(Modifier.height(PixelSpace.Snug))
                    androidx.compose.material3.Text(
                        text = limitedCountLabel(summary.enabledCount),
                        style = MonoTypeScale.Body,
                        color = PixelPalette.Text,
                    )
                    Spacer(Modifier.height(PixelSpace.Tight))
                    androidx.compose.material3.Text(
                        text = "Opening one requires winning a hand of blackjack.",
                        style = MonoTypeScale.Metadata,
                        color = TextSoft,
                    )
                }

                is HomeSummary.Active -> {
                    val row = summary.row
                    val capped = row.lockout == HomeLockout.DAILY_CAP
                    val locked = row.state == EnforcementState.LOCKED
                    androidx.compose.material3.Text(
                        text = row.label,
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                    Spacer(Modifier.height(PixelSpace.Snug))
                    PixelStatusBadge(
                        tone = if (locked) BadgeTone.LOCKED else BadgeTone.GRANTED,
                        labelOverride = if (capped) "CAPPED" else null,
                    )
                    Spacer(Modifier.height(PixelSpace.Base))
                    if (capped) {
                        // A cap resets at the next local midnight: a wall clock, not a countdown.
                        HomeResetFrame(resetText = row.resetText)
                    } else {
                        PixelTimer(
                            timeText = row.remainingText,
                            caption = if (locked) "LOCKED FOR" else "ACCESS LEFT",
                            tone = if (locked) TimerTone.LOCKED else TimerTone.GRANTED,
                        )
                    }
                }
            }
        }
    }
}

/** One limited app: label, its live state badge, and the package id underneath. */
@Composable
private fun HomeTargetPanel(row: HomeRow) {
    val capped = row.lockout == HomeLockout.DAILY_CAP
    val stateWord = row.stateWord()
    // A capped row has no MM:SS interval to read; name the wall clock it resets at instead.
    val detail = if (capped) "RESETS ${row.resetText}" else row.remainingText
    val description = listOf(row.label, stateWord, detail)
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
                // A null override leaves the badge reading its own word (GRANTED / LOCKED).
                val tone = when (row.badge()) {
                    HomeBadge.GRANTED -> BadgeTone.GRANTED
                    HomeBadge.LOCKED -> BadgeTone.LOCKED
                }
                PixelStatusBadge(tone, labelOverride = row.badgeText())
            }
            Spacer(Modifier.height(PixelSpace.Tight))
            androidx.compose.material3.Text(
                text = row.packageName,
                style = MonoTypeScale.PackageId,
                color = TextSoft,
            )
        }
    }
}

/** The amber cursor block after the wordmark. */
private val CursorBlockSize = 8.dp

/** "1 APP LIMITED" / "3 APPS LIMITED" — the panel's live enabled count. */
private fun limitedCountLabel(count: Int): String =
    if (count == 1) "1 APP LIMITED" else "$count APPS LIMITED"

/**
 * A daily-cap lockout ends at the next local midnight, so it is drawn as that wall
 * clock in the timer's frame grammar rather than as a ticking MM:SS countdown.
 */
@Composable
private fun HomeResetFrame(resetText: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .background(PixelPalette.Surface)
                .pixelShadow()
                .pixelSteppedCorners()
                .border(PixelSpace.Stroke, PixelPalette.Primary)
                .padding(horizontal = PixelSpace.Room, vertical = PixelSpace.Snug),
        ) {
            androidx.compose.material3.Text(
                text = resetText,
                style = PixelTypeScale.Timer,
                fontFamily = PixelFont,
                color = PixelPalette.Primary,
            )
        }
        androidx.compose.material3.Text(
            text = "RESETS",
            style = PixelTypeScale.Badge,
            color = TextSoft,
            modifier = Modifier.padding(top = PixelSpace.Snug),
        )
    }
}
