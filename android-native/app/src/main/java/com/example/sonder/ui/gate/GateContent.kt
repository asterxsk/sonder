package com.example.sonder.ui.gate

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
    /** Epoch millis this lockout ends at, or 0 when the blocker is showing the table. */
    lockoutUntilMillis: Long,
    onDeal: () -> Unit,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onAccessGranted: () -> Unit,
    onPlayAgain: () -> Unit,
) {
    // A lockout is a countdown, so the panel needs a clock of its own: the blocker window
    // is composed once and the coordinator only re-decides when the deadline passes, so a
    // remaining-time value captured at compose time would sit frozen for the whole wait —
    // the panel said "TIME LEFT ON THIS LOCKOUT" and then never moved.
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lockoutUntilMillis) {
        if (lockoutUntilMillis <= 0L) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            if (lockoutUntilMillis <= nowMillis) break
            delay(1_000)
        }
    }
    val lockoutRemainingMillis = (lockoutUntilMillis - nowMillis).coerceAtLeast(0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        // A wait can begin mid-session: the hand that takes the debt to its ceiling writes a
        // lockout while the table is still open, so the panel follows the controller's state
        // as well as the value the blocker was raised with. At the ceiling the player waits
        // the debt out — waiting is what serves it — rather than being dealt another hand.
        //
        // A settled hand is the one exception: the result of the hand that reached the
        // ceiling stays on screen until the player acknowledges it, since the wait panel
        // offers no cards either way and losing the result would just be confusing.
        val waiting = maxOf(lockoutRemainingMillis, state.debtLockRemainingMillis)
        if (waiting > 0 && state.phase != TableState.Phase.RESOLVED) {
            LockoutBlocker(label = label, remainingMillis = waiting)
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
    // §14 status badge row. The badge keys off whether access was actually granted, not
    // off "won with no debt": a win with a spent daily cap grants nothing, and "GRANTED"
    // over a gate the coordinator is about to raise again is a lie.
    when {
        state.showResult && state.lastHandGranted ->
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
            // Same test as the badge, and for the same reason: only a hand that actually
            // granted access may offer CONTINUE, or the user is sent back to a blocked app.
            val won = state.lastHandGranted
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
    val suitColor =
        if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) PixelPalette.Danger else PixelPalette.CardInk
    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 74.dp)
            .padding(2.dp)
            .background(if (faceDown) PixelPalette.Primary else PixelPalette.CardFace)
            .border(2.dp, if (faceDown) PixelPalette.PrimaryDark else PixelPalette.CardInk)
            // Without this, TalkBack reads a face-down card as the placeholder it is drawn
            // with — literally "two of spades" — rather than as the card back it depicts.
            .semantics {
                contentDescription =
                    if (faceDown) "Face-down card" else "${card.display} of ${suitName(suit)}"
            },
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

/** Spoken form of a suit, for screen readers. */
private fun suitName(suit: Suit): String = when (suit) {
    Suit.SPADES -> "spades"
    Suit.HEARTS -> "hearts"
    Suit.DIAMONDS -> "diamonds"
    Suit.CLUBS -> "clubs"
}
