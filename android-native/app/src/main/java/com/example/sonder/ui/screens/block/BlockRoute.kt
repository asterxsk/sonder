package com.example.sonder.ui.screens.block

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
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
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

    // A grant exists only when the last hand wrote one; a win under a spent cap grants
    // nothing. "Cap blocking" is that lockout with no such grant in flight — a day-wide
    // state the invite copy and the controls must not present as a playable table.
    val grantedWin = state.showResult && state.lastOutcome == HandOutcome.WIN && state.lastHandGranted
    val capBlocking = state.capLocked && !grantedWin

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(PixelSpace.Room),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(PixelSpace.Snug))

        // §14 status badge row. A cap lockout reads the same here as in the overlay: a
        // day-wide lockout, not the idle PLAYING table it otherwise looks like.
        when {
            grantedWin ->
                PixelStatusBadge(BadgeTone.GRANTED)
            capBlocking ->
                PixelStatusBadge(BadgeTone.COOLDOWN, labelOverride = "CAPPED TODAY")
            state.showResult && state.lastOutcome == HandOutcome.WIN ->
                PixelStatusBadge(BadgeTone.GRANTED)
            state.debtMinutes > 0 ->
                PixelStatusBadge(BadgeTone.COOLDOWN, labelOverride = "DEBT ${state.debtMinutes} MIN")
            else ->
                PixelStatusBadge(BadgeTone.PLAYING)
        }

        Spacer(Modifier.height(PixelSpace.Base))

        PixelPanel(modifier = Modifier.fillMaxWidth()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    text = targetPackage.substringAfterLast('.').uppercase(),
                    style = PixelTypeScale.ScreenTitle,
                    fontFamily = PixelFont,
                    color = PixelPalette.Text,
                )
                Spacer(Modifier.height(PixelSpace.Tight))
                androidx.compose.material3.Text(
                    text = if (capBlocking) {
                        "DAILY LIMIT REACHED — BACK TOMORROW"
                    } else {
                        "WIN A HAND FOR ${formatAccess(state.winGrantMillis)} ACCESS"
                    },
                    style = MonoTypeScale.Metadata,
                    color = TextSoft,
                )
                // Only apps the user capped show a daily allowance line; uncapped apps
                // behave exactly as before.
                state.dailyRemainingMillis?.let { remaining ->
                    Spacer(Modifier.height(PixelSpace.Tight))
                    androidx.compose.material3.Text(
                        text = if (remaining <= 0L) {
                            "TODAY'S ALLOWANCE SPENT"
                        } else {
                            "TODAY'S ALLOWANCE ${formatAccess(remaining)} LEFT"
                        },
                        style = MonoTypeScale.Metadata,
                        color = TextSoft,
                    )
                }
            }
        }

        Spacer(Modifier.height(PixelSpace.Base))

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

                Spacer(Modifier.height(PixelSpace.Base))
                PixelDivider()
                Spacer(Modifier.height(PixelSpace.Base))

                // Player hand — same pinned label/total, lazy card strip.
                HandRow(label = "YOU", cards = playerSlots, total = state.playerHand?.total)

                Spacer(Modifier.height(PixelSpace.Base))

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
                            .border(PixelSpace.Stroke, messageColor)
                            .padding(horizontal = PixelSpace.Base, vertical = PixelSpace.Snug),
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
                // A cap lockout has no hand to play for; offer the exit instead of a deal.
                if (capBlocking) {
                    PixelButton(
                        text = "CAPPED — BACK TOMORROW",
                        onClick = onDismiss,
                        style = PixelButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    PixelButton(text = "♠  DEAL HAND", onClick = { viewModel.deal() }, modifier = Modifier.fillMaxWidth())
                }
            }
            com.example.sonder.ui.screens.blackjack.TableState.Phase.PLAYER_TURN -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PixelSpace.Base),
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
                // Success keys off whether a grant was actually written, not off "won with
                // no debt": a debt-free win under a spent cap grants nothing, and treating
                // it as success would finish() the gate with no grant and re-raise it.
                when {
                    grantedWin -> PixelButton(
                        text = "CONTINUE →",
                        onClick = onAccessGranted,
                        style = PixelButtonStyle.SUCCESS,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    capBlocking -> PixelButton(
                        text = "CAPPED — BACK TOMORROW",
                        onClick = onDismiss,
                        style = PixelButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> PixelButton(
                        text = "PLAY AGAIN",
                        onClick = { viewModel.continueAfterResult() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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

        Spacer(Modifier.height(PixelSpace.Base))

        // §13 mini timer for debt
        if (state.debtMinutes > 0) {
            com.example.sonder.ui.kit.PixelTimer(
                timeText = String.format(Locale.ROOT, "%02d:00", state.debtMinutes),
                caption = "LOCKOUT DEBT — WIN TO PAY IT OFF",
                tone = TimerTone.LOCKED,
            )
        }
        Spacer(Modifier.height(PixelSpace.Snug))
    }
}

@Composable
private fun PixelDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PixelSpace.Stroke)
            .background(PixelPalette.BorderDark),
    )
}

/** mm:ss for a grant / allowance figure, matching the timer digits elsewhere. */
private fun formatAccess(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
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
            color = TextSoft,
        )
        Spacer(Modifier.size(PixelSpace.Snug))
        LazyRow(modifier = Modifier.weight(1f)) {
            items(cards) { slot -> PlayingCard(slot.card, faceDown = slot.faceDown) }
        }
        total?.let {
            Spacer(Modifier.size(PixelSpace.Snug))
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
    // design_v3 §18: the card flip is instant — no tween, no spring, no rotation.
    val suitColor = if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) PixelPalette.Danger else PixelPalette.CardInk
    Box(
        modifier = Modifier
            .size(width = CardWidth, height = CardHeight)
            .padding(PixelSpace.Stroke)
            .background(if (faceDown) PixelPalette.Primary else PixelPalette.CardFace)
            .border(
                PixelSpace.Stroke,
                if (faceDown) PixelPalette.PrimaryDark else PixelPalette.CardInk,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!faceDown) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize().padding(vertical = PixelSpace.Snug),
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
                color = PixelPalette.CardInk,
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

/**
 * §9's card. 52×74 is the nominal size the rank and suit columns are laid out against;
 * the visible face is inset by one stroke so adjacent cards in a hand never touch.
 */
private val CardWidth = 52.dp
private val CardHeight = 74.dp
