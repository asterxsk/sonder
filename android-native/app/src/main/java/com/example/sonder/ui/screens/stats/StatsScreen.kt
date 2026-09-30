package com.example.sonder.ui.screens.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.data.db.HandEntity
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.PixelPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stats: wins/losses ledger + recent hand history. Quiet, not dashboard-y (§21).
 * The dock, background, and system insets come from the shared [PaddingValues].
 */
@Composable
fun StatsScreen(
    contentPadding: PaddingValues,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val timeFmt = remember { SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = PixelSpace.Room),
    ) {
        Spacer(Modifier.height(PixelSpace.Room))
        androidx.compose.material3.Text(
            "STATS",
            style = PixelTypeScale.ScreenTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(PixelSpace.Room))

        Row(horizontalArrangement = Arrangement.spacedBy(PixelSpace.Base)) {
            SummaryPanel(
                glyph = "✓",
                label = "WINS",
                value = state.wins,
                tone = PixelPalette.Success,
                modifier = Modifier.weight(1f),
            )
            SummaryPanel(
                glyph = "▣",
                label = "LOSSES",
                value = state.losses,
                tone = PixelPalette.Danger,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(PixelSpace.Section))
        androidx.compose.material3.Text(
            "RECENT HANDS",
            style = PixelTypeScale.SectionTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Text,
        )
        Spacer(Modifier.height(PixelSpace.Snug))

        when {
            // Room has not answered yet — say so rather than showing an empty ledger.
            !state.loaded -> FramedNote(
                title = "READING LEDGER",
                body = "Pulling your hand history from the console.",
            )
            state.hands.isEmpty() -> FramedNote(
                title = "NO HANDS YET",
                body = "Results appear here after the first completed hand.",
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
            ) {
                items(state.hands, key = { it.id }) { hand -> HandRow(hand, timeFmt) }
            }
        }
    }
}

/** One ledger count. Glyph and label share a line so the number keeps its full size. */
@Composable
private fun SummaryPanel(
    glyph: String,
    label: String,
    value: Int,
    tone: Color,
    modifier: Modifier = Modifier,
) {
    PixelPanel(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(glyph, color = tone)
                Spacer(Modifier.width(6.dp))
                androidx.compose.material3.Text(
                    label,
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = PixelPalette.Muted,
                )
            }
            androidx.compose.material3.Text(
                "$value",
                style = PixelTypeScale.Timer,
                fontFamily = PixelFont,
                color = tone,
            )
        }
    }
}

/** Framed explanatory state — visually a panel, not a blank screen. */
@Composable
private fun FramedNote(title: String, body: String) {
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.compose.material3.Text(
                title,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
            )
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(
                body,
                style = MonoTypeScale.Body,
                color = TextSoft,
            )
        }
    }
}

@Composable
private fun HandRow(hand: HandEntity, timeFmt: SimpleDateFormat) {
    PixelPanel {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    // The stored label, which is the app's own name at the time of the hand.
                    // Rows written before the label existed fall back to the package name:
                    // the last dot-segment used to be printed here, which turned
                    // com.instagram.android into "android" — the word every Instagram and
                    // YouTube hand was filed under.
                    androidx.compose.material3.Text(
                        hand.label.ifBlank { hand.packageName },
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                    androidx.compose.material3.Text(
                        timeFmt.format(Date(hand.playedAtMillis)),
                        style = MonoTypeScale.PackageId,
                        color = TextSoft,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    androidx.compose.material3.Text(
                        when (hand.outcome) {
                            "WIN" -> "✓ WIN"
                            "LOSE" -> "▣ LOSS"
                            else -> "— PUSH"
                        },
                        style = PixelTypeScale.Badge,
                        fontFamily = PixelFont,
                        color = when (hand.outcome) {
                            "WIN" -> PixelPalette.Success
                            "LOSE" -> PixelPalette.Danger
                            else -> PixelPalette.Muted
                        },
                    )
                    androidx.compose.material3.Text(
                        "debt ${hand.debtAfterMillis / 60_000}m",
                        style = MonoTypeScale.PackageId,
                        color = TextSoft,
                    )
                }
            }

            // The hands, as they were dealt. A row from before cards were recorded has
            // neither line, and the ledger simply says less about that hand rather than
            // printing an empty one.
            if (hand.playerCards.isNotBlank() || hand.dealerCards.isNotBlank()) {
                Spacer(Modifier.height(PixelSpace.Snug))
                hand.playerCards.takeIf { it.isNotBlank() }?.let {
                    HandLine(who = "YOU", cards = it)
                }
                hand.dealerCards.takeIf { it.isNotBlank() }?.let {
                    HandLine(who = "DEALER", cards = it)
                }
            }
        }
    }
}

/** One side of a played hand: who, and the cards with their total. */
@Composable
private fun HandLine(who: String, cards: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.material3.Text(
            who,
            style = MonoTypeScale.PackageId,
            color = PixelPalette.Muted,
            modifier = Modifier.width(WhoColumnWidth),
        )
        androidx.compose.material3.Text(
            cards,
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = TextSoft,
        )
    }
}

/** Wide enough for "DEALER" at the monospace metadata scale, so the two lines align. */
private val WhoColumnWidth = 56.dp

