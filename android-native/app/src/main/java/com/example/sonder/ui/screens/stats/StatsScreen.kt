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
import com.example.sonder.ui.kit.CardPadding
import com.example.sonder.ui.kit.PixelCard
import com.example.sonder.ui.kit.PixelCardTitle
import com.example.sonder.ui.kit.PixelChip
import com.example.sonder.ui.kit.PixelChipTone
import com.example.sonder.ui.kit.PixelChipVariant
import com.example.sonder.ui.kit.PixelEmptyState
import com.example.sonder.ui.kit.PixelGroupLabel
import com.example.sonder.ui.kit.PixelSectionHeader
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
        PixelSectionHeader(
            title = "STATS",
            subtitle = "Every hand you have played, as it was dealt",
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
        PixelGroupLabel(text = "RECENT HANDS")
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
    PixelCard(modifier = modifier, padding = CardPadding.SM) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Glyph and word first, small and quiet, then the count beneath them at display
            // size: the label is what the reader is looking for and the number is what they
            // take away, and in that order the tile reads top-down in one glance.
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(glyph, color = tone)
                Spacer(Modifier.width(PixelSpace.Tight))
                androidx.compose.material3.Text(
                    label,
                    style = MonoTypeScale.Metadata,
                    color = TextSoft,
                )
            }
            androidx.compose.material3.Text(
                "$value",
                // Not the pixel face: its nought is a hollow ring. See [MonoTypeScale.Count].
                style = MonoTypeScale.Count,
                color = tone,
            )
        }
    }
}

/**
 * Framed explanatory state — a card, not a blank screen.
 *
 * Both of this screen's not-yet states are ordinary: Room has not answered, or the ledger
 * is genuinely empty. The card is drawn in the neutral tone for exactly that reason — a
 * ledger with no hands is a fact about the user, not a fault in the app.
 */
@Composable
private fun FramedNote(title: String, body: String) {
    PixelEmptyState(title = title, body = body)
}

@Composable
private fun HandRow(hand: HandEntity, timeFmt: SimpleDateFormat) {
    PixelCard(padding = CardPadding.SM) {
        Column(modifier = Modifier.fillMaxWidth()) {
            PixelCard.Header {
                PixelCardTitle(
                    // The stored label, which is the app's own name at the time of the hand.
                    // Rows written before the label existed fall back to the package name:
                    // the last dot-segment used to be printed here, which turned
                    // com.instagram.android into "android" — the word every Instagram and
                    // YouTube hand was filed under.
                    text = hand.label.ifBlank { hand.packageName },
                )
                PixelChip(
                    // The outcome is the one thing a scan of this ledger is looking for, so
                    // it is the one thing that gets a filled chip rather than an outline.
                    label = when (hand.outcome) {
                        "WIN" -> "WIN"
                        "LOSE" -> "LOSS"
                        else -> "PUSH"
                    },
                    glyph = when (hand.outcome) {
                        "WIN" -> "✓"
                        "LOSE" -> "▣"
                        else -> "—"
                    },
                    tone = when (hand.outcome) {
                        "WIN" -> PixelChipTone.SUCCESS
                        "LOSE" -> PixelChipTone.DANGER
                        else -> PixelChipTone.NEUTRAL
                    },
                    variant = if (hand.outcome == "WIN" || hand.outcome == "LOSE") {
                        PixelChipVariant.SOLID
                    } else {
                        PixelChipVariant.OUTLINE
                    },
                )
            }
            PixelCard.Meta(
                // The timestamp and the two numbers that explain a ledger whose rows are all
                // the same shape, on one line under the name.
                text = "${timeFmt.format(Date(hand.playedAtMillis))}  ·  " +
                    "bet ${hand.stakeMillis / 60_000}m · bank ${hand.bankAfterMillis / 60_000}m",
                modifier = Modifier.padding(top = PixelSpace.Tight),
            )

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

