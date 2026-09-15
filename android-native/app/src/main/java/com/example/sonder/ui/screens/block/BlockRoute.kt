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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    val state by viewModel.state.collectAsState()

    LaunchedEffect(targetPackage) { viewModel.start(targetPackage) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

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

        Spacer(Modifier.height(16.dp))

        // §9 blackjack table
        PixelPanel(
            modifier = Modifier.fillMaxWidth().weight(1f),
            fillColor = PixelPalette.Panel,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Dealer row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(
                        "DEALER",
                        style = PixelTypeScale.Badge,
                        fontFamily = PixelFont,
                        color = PixelPalette.Muted,
                    )
                    Spacer(Modifier.size(8.dp))
                    state.dealerUp?.let { up ->
                        PlayingCard(up.cards.first(), faceDown = false)
                        if (state.dealerFull != null) {
                            // Full dealer hand once revealed.
                            state.dealerFull!!.cards.drop(1).forEach { PlayingCard(it, faceDown = false) }
                        } else {
                            PlayingCard(Rank.TWO, Suit.SPADES, faceDown = true) // card back
                        }
                    }
                    state.dealerFull?.let {
                        Spacer(Modifier.size(8.dp))
                        androidx.compose.material3.Text(
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

                // Player row
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Text(
                        "YOU",
                        style = PixelTypeScale.Badge,
                        fontFamily = PixelFont,
                        color = PixelPalette.Muted,
                    )
                    Spacer(Modifier.size(8.dp))
                    state.playerHand?.cards?.forEach { PlayingCard(it, faceDown = false) }
                    state.playerHand?.let {
                        Spacer(Modifier.size(8.dp))
                        androidx.compose.material3.Text(
                            "${it.total}",
                            style = PixelTypeScale.SectionTitle,
                            fontFamily = PixelFont,
                            color = PixelPalette.Text,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Message line
                androidx.compose.material3.Text(
                    text = state.message,
                    style = MonoTypeScale.Body,
                    color = PixelPalette.Primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(12.dp))

        // §13 mini timer for debt
        if (state.debtMinutes > 0) {
            com.example.sonder.ui.kit.PixelTimer(
                timeText = String.format("%02d:00", state.debtMinutes),
                caption = "LOCKOUT DEBT — WIN TO PAY IT OFF",
                tone = TimerTone.LOCKED,
            )
        }
        Spacer(Modifier.height(12.dp))
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

/** White/off-white card face per §9 (readability first), pixel-framed, 2-frame flip feel. */
@Composable
fun PlayingCard(card: Rank, suit: Suit, faceDown: Boolean) {
    val rotation by animateFloatAsState(
        targetValue = if (faceDown) 0f else 1f,
        animationSpec = snap(), // discrete flip, no spring
        label = "cardFlip",
    )
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 74.dp)
            .padding(2.dp)
            .background(if (faceDown) PixelPalette.Primary else PixelPalette.CardFace)
            .border(2.dp, if (faceDown) PixelPalette.PrimaryDark else PixelPalette.CardInk),
        contentAlignment = Alignment.Center,
    ) {
        if (!faceDown) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.material3.Text(
                    text = "${card.display}${suitGlyph(suit)}",
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
