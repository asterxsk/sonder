package com.example.sonder.ui.gate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.Rank
import com.example.sonder.domain.model.Suit
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.BadgeTone
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.PixelTimer
import com.example.sonder.ui.kit.TimerTone

/**
 * The blocker surface (design_v3 §10–12), hosted either inside the service-owned
 * system overlay window or, for previews, any Compose host. Stateless: all state
 * and actions come from the caller, so it can live outside an Activity.
 *
 * Lockout mode shows the "WAIT IT OUT" panel with no table; gate mode shows the
 * blackjack table with HIT/STAND and the unlock action.
 */
@Composable
fun GateContent(
    label: String,
    state: TableState,
    lockoutRemainingMillis: Long,
    onDeal: () -> Unit,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onAccessGranted: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        if (lockoutRemainingMillis > 0) {
            LockoutBlocker(label = label, remainingMillis = lockoutRemainingMillis)
        } else {
            BlackjackBlocker(
                label = label,
                state = state,
                onDeal = onDeal,
                onHit = onHit,
                onStand = onStand,
                onAccessGranted = onAccessGranted,
                onPlayAgain = onPlayAgain,
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LockoutBlocker(label: String, remainingMillis: Long) {
    PixelStatusBadge(BadgeTone.COOLDOWN, labelOverride = "LOCKED")

    Spacer(Modifier.height(16.dp))

    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label.uppercase(),
                style = PixelTypeScale.ScreenTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "LOCKED — WAIT IT OUT",
                style = MonoTypeScale.Metadata,
                color = PixelPalette.Danger,
            )
        }
    }

    Spacer(Modifier.height(24.dp))

    PixelTimer(
        timeText = formatRemaining(remainingMillis),
        caption = "TIME LEFT ON THIS LOCKOUT",
        tone = TimerTone.LOCKED,
    )

    Spacer(Modifier.height(16.dp))

    Text(
        text = "OPENING THIS APP AGAIN WILL BLOCK IT AGAIN UNTIL THE TIMER RUNS OUT.",
        style = MonoTypeScale.Metadata,
        color = PixelPalette.Muted,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun BlackjackBlocker(
    label: String,
    state: TableState,
    onDeal: () -> Unit,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onAccessGranted: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    // §14 status badge row
    when {
        state.showResult && state.lastOutcome == HandOutcome.WIN ->
            PixelStatusBadge(BadgeTone.GRANTED)
        state.debtMinutes > 0 ->
            PixelStatusBadge(BadgeTone.COOLDOWN, labelOverride = "DEBT ${state.debtMinutes} MIN")
        else ->
            PixelStatusBadge(BadgeTone.PLAYING)
    }

    Spacer(Modifier.height(16.dp))

    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label.uppercase(),
                style = PixelTypeScale.ScreenTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "IS BLOCKED — WIN A HAND FOR 5:00 ACCESS",
                style = MonoTypeScale.Metadata,
                color = PixelPalette.Muted,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // §9 blackjack table
    PixelPanel(
        modifier = Modifier.fillMaxWidth(),
        fillColor = PixelPalette.Panel,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "DEALER",
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = PixelPalette.Muted,
                )
                Spacer(Modifier.size(8.dp))
                state.dealerUp?.let { up ->
                    PlayingCard(up.cards.first(), faceDown = false)
                    val full = state.dealerFull
                    if (full != null) {
                        full.cards.drop(1).forEach { PlayingCard(it, faceDown = false) }
                    } else {
                        PlayingCard(Rank.TWO, Suit.SPADES, faceDown = true) // card back
                    }
                }
                state.dealerFull?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "${it.total}",
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            PixelDivider()
            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "YOU",
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = PixelPalette.Muted,
                )
                Spacer(Modifier.size(8.dp))
                state.playerHand?.cards?.forEach { PlayingCard(it, faceDown = false) }
                state.playerHand?.let {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "${it.total}",
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = state.message,
                style = MonoTypeScale.Body,
                color = PixelPalette.Primary,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    // Controls: IDLE → DEAL, PLAYER_TURN → HIT/STAND, RESOLVED → CONTINUE
    when (state.phase) {
        TableState.Phase.IDLE -> {
            PixelButton(text = "♠  DEAL HAND", onClick = onDeal, modifier = Modifier.fillMaxWidth())
        }

        TableState.Phase.PLAYER_TURN -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelButton(text = "HIT", onClick = onHit, modifier = Modifier.weight(1f))
                PixelButton(
                    text = "STAND",
                    onClick = onStand,
                    style = PixelButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        TableState.Phase.RESOLVED -> {
            val won = state.lastOutcome == HandOutcome.WIN && state.debtMinutes == 0L
            PixelButton(
                text = if (won) "CONTINUE →" else "PLAY AGAIN",
                onClick = { if (won) onAccessGranted() else onPlayAgain() },
                style = if (won) PixelButtonStyle.SUCCESS else PixelButtonStyle.PRIMARY,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> {
            Text(
                "DEALING…",
                style = PixelTypeScale.Badge,
                fontFamily = PixelFont,
                color = PixelPalette.Muted,
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    // §13 mini timer for debt
    if (state.debtMinutes > 0) {
        PixelTimer(
            timeText = String.format("%02d:00", state.debtMinutes),
            caption = "LOCKOUT DEBT — WIN TO PAY IT OFF",
            tone = TimerTone.LOCKED,
        )
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

internal fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return String.format("%02d:%02d", totalSeconds / 60, totalSeconds % 60)
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
    val suitColor =
        if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) PixelPalette.Danger else PixelPalette.CardInk
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = suitGlyph(suit),
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = suitColor,
                )
                Text(
                    text = card.display,
                    style = PixelTypeScale.CardFace,
                    fontFamily = PixelFont,
                    color = PixelPalette.CardInk,
                )
            }
        } else {
            // Tiny Sonder motif on the back: crescent + S (§9: cat/crescent/S sprite).
            Text(
                text = "☾S",
                style = PixelTypeScale.Badge,
                fontFamily = PixelFont,
                color = PixelPalette.Bg,
            )
        }
    }
}

/** Overload for the domain Card type (avoids name collision). */
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
