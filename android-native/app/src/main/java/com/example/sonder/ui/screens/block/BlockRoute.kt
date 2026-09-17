package com.example.sonder.ui.screens.block

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.domain.model.Card
import com.example.sonder.domain.model.Hand
import com.example.sonder.ui.screens.blackjack.BlackjackViewModel
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.Rank
import com.example.sonder.domain.model.Suit
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.BadgeTone
import com.example.sonder.ui.kit.TimerTone
import java.util.Locale

/**
 * The gate (design_v3 §10–12): blocked-app frame, blackjack table per §9,
 * win/loss states per §11–12. Discrete stepped animation only — no springs.
 */
@Composable
fun BlockRoute(
    targetPackage: String,
    onAccessGranted: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: BlackjackViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(targetPackage) { viewModel.start(targetPackage) }

    // Cards are resolved into flat slots so each hand renders from a lazy row.
    val dealerSlots = state.dealerUp?.let { up ->
        val full = state.dealerFull
        if (full != null) {
            // Full dealer hand once revealed.
            full.cards.map { CardSlot(it, faceDown = false) }
        } else {
            listOf(
                CardSlot(up.cards.first(), faceDown = false),
                CardSlot(Card(Rank.TWO, Suit.SPADES), faceDown = true), // card back
            )
        }
    } ?: emptyList()
    val playerSlots = state.playerHand?.cards?.map { CardSlot(it, faceDown = false) } ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        // §14 status badge row
        when {
            state.showResult && state.lastOutcome == HandOutcome.WIN ->
                PixelStatusBadge(BadgeTone.GRANTED)
            state.debtMinutes > 0 ->
                PixelStatusBadge(BadgeTone.COOLDOWN, labelOverride = "DEBT ${state.debtMinutes} MIN")
            else ->
                PixelStatusBadge(BadgeTone.PLAYING)
        }

        Spacer(Modifier.height(12.dp))

        PixelPanel(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    text = targetPackage.substringAfterLast('.').uppercase(),
                    style = PixelTypeScale.ScreenTitle,
                    fontFamily = PixelFont,
                    color = PixelPalette.Text,
                )
                Spacer(Modifier.height(4.dp))
                androidx.compose.material3.Text(
                    text = "WIN A HAND FOR 5:00 ACCESS",
                    style = MonoTypeScale.Metadata,
                    color = PixelPalette.Muted,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // §9 blackjack table
        PixelPanel(
            modifier = Modifier.fillMaxWidth().weight(1f),
            fillColor = PixelPalette.Panel,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Dealer hand — label and total stay pinned, cards scroll lazily.
                HandRow(label = "DEALER", cards = dealerSlots, total = state.dealerFull?.total)

                Spacer(Modifier.height(14.dp))
                PixelDivider()
                Spacer(Modifier.height(14.dp))

                // Player hand — same pinned label/total, lazy card strip.
                HandRow(label = "YOU", cards = playerSlots, total = state.playerHand?.total)

                Spacer(Modifier.height(14.dp))

                // Message line — the table's one line of result copy, so it keeps the
                // frame and the colour while everything around it stays a hairline.
                if (state.message.isNotEmpty()) {
                    val messageColor = when {
                        state.showResult && state.lastOutcome == HandOutcome.WIN -> PixelPalette.Success
                        state.showResult && state.lastOutcome == HandOutcome.LOSE -> PixelPalette.Danger
                        else -> PixelPalette.Primary
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PixelPalette.Bg)
                            .border(2.dp, messageColor)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.material3.Text(
                            text = state.message,
                            style = MonoTypeScale.Body,
                            color = messageColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Controls: IDLE → DEAL, PLAYER_TURN → HIT/STAND, RESOLVED → CONTINUE
        when (state.phase) {
            com.example.sonder.ui.screens.blackjack.TableState.Phase.IDLE -> {
                PixelButton(text = "♠  DEAL HAND", onClick = { viewModel.deal() }, modifier = Modifier.fillMaxWidth())
            }
            com.example.sonder.ui.screens.blackjack.TableState.Phase.PLAYER_TURN -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PixelButton(
                        text = "HIT",
                        onClick = { viewModel.hit() },
                        modifier = Modifier.weight(1f),
                    )
                    PixelButton(
                        text = "STAND",
                        onClick = { viewModel.stand() },
                        style = PixelButtonStyle.SECONDARY,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            com.example.sonder.ui.screens.blackjack.TableState.Phase.RESOLVED -> {
                val won = state.lastOutcome == HandOutcome.WIN && state.debtMinutes == 0L
                PixelButton(
                    text = when {
                        state.lastOutcome == HandOutcome.WIN && won -> "CONTINUE →"
                        else -> "PLAY AGAIN"
                    },
                    onClick = {
                        if (won) onAccessGranted() else viewModel.continueAfterResult()
                    },
                    style = if (won) PixelButtonStyle.SUCCESS else PixelButtonStyle.PRIMARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                androidx.compose.material3.Text(
                    "DEALING…",
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = PixelPalette.Muted,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // §13 mini timer for debt
        if (state.debtMinutes > 0) {
            com.example.sonder.ui.kit.PixelTimer(
                timeText = String.format(Locale.ROOT, "%02d:00", state.debtMinutes),
                caption = "LOCKOUT DEBT — WIN TO PAY IT OFF",
                tone = TimerTone.LOCKED,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PixelDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(PixelPalette.BorderDark),
    )
}

/** One slot on the table: a card plus whether it is still face down. */
private data class CardSlot(val card: Card, val faceDown: Boolean)

/**
 * A labelled hand. The label and total stay pinned while the cards sit in a lazy
 * row, so a hand wider than the viewport stays reachable and off-screen cards are
 * never composed (§9).
 */
@Composable
private fun HandRow(label: String, cards: List<CardSlot>, total: Int?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            label,
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = PixelPalette.Muted,
        )
        Spacer(Modifier.size(8.dp))
        LazyRow(modifier = Modifier.weight(1f)) {
            items(cards) { slot -> PlayingCard(slot.card, faceDown = slot.faceDown) }
        }
        total?.let {
            Spacer(Modifier.size(8.dp))
            androidx.compose.material3.Text(
                "$it",
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
            )
        }
    }
}

/** White/off-white card face per §9 (readability first), pixel-framed.
 *  Layout: suit (house) on top, rank on bottom — so two-digit ranks (10)
 *  have their own line and never clip out of the card frame. */
@Composable
fun PlayingCard(card: Rank, suit: Suit, faceDown: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (faceDown) 0f else 1f,
        animationSpec = snap(), // discrete flip, no spring
        label = "cardFlip",
    )
    val suitColor = if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) PixelPalette.Danger else PixelPalette.CardInk
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 74.dp)
            .padding(2.dp)
            .background(if (faceDown) PixelPalette.Primary else PixelPalette.CardFace)
            .border(2.dp, if (faceDown) PixelPalette.PrimaryDark else PixelPalette.CardInk),
        contentAlignment = Alignment.Center,
    ) {
        if (!faceDown) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize().padding(vertical = 6.dp),
            ) {
                androidx.compose.material3.Text(
                    text = suitGlyph(suit),
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = suitColor,
                )
                androidx.compose.material3.Text(
                    text = card.display,
                    style = PixelTypeScale.CardFace,
                    fontFamily = PixelFont,
                    color = PixelPalette.CardInk,
                )
            }
        } else {
            // Tiny Sonder motif on the back: crescent + S (§9: cat/crescent/S sprite).
            androidx.compose.material3.Text(
                text = "☾S",
                style = PixelTypeScale.Badge,
                fontFamily = PixelFont,
                color = PixelPalette.Bg,
            )
        }
    }
}

// Overload for domain Card type (avoids name collision).
@Composable
fun PlayingCard(card: com.example.sonder.domain.model.Card, faceDown: Boolean) {
    PlayingCard(card.rank, card.suit, faceDown)
}

private fun suitGlyph(suit: Suit): String = when (suit) {
    Suit.SPADES -> "♠"
    Suit.HEARTS -> "♥"
    Suit.DIAMONDS -> "♦"
    Suit.CLUBS -> "♣"
}
