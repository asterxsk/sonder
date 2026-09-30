package com.example.sonder.ui.gate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.example.sonder.domain.model.Card
import com.example.sonder.domain.model.Rank
import com.example.sonder.domain.model.Suit
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
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
 *
 * CLOSE sits below whichever of the two is showing, rendered once here rather than inside
 * each of them. It is the way *out* — the way in is a hand — and it is the only control this
 * screen has that is always present, which is the point: §10 through §12 never specify one,
 * and without it a blocked app is a wall the user has to know to press Back or Home to
 * escape, which inside an app that intercepts Back is not an escape at all. It renders in
 * every state, the 60-minute debt ceiling included: the way out is never something the user
 * has to earn or outlast.
 *
 * A settled loss gets a second exit directly under its replay button (see BlackjackBlocker),
 * for the reason in §10 — the exit has to be where the decision is, not below the fold.
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
    onClose: () -> Unit,
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

    // Centred rather than top-anchored: the panel is a fixed stack, and against a tall
    // screen a top-anchored one leaves the whole lower half of the blocker empty. The
    // centring has to come from the box rather than from Column(verticalArrangement = ...):
    // the column scrolls, and a scrolling column is measured against an unbounded height, so
    // it has no spare space to distribute and the arrangement never applies.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The panel is a fixed stack: the badge, two framed blocks, the controls and
                // the timer all have their own heights, and on a short screen they overrun
                // the bottom — which is where CLOSE lives. §19 forbids clipping an
                // interactive control, so the column scrolls rather than letting the way out
                // be the thing that is cut off.
                .verticalScroll(rememberScrollState())
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
                    onClose = onClose,
                )
            }

            Spacer(Modifier.height(12.dp))

            // The way out, in both states and below the state's own controls so it never competes
            // with the way in. Secondary, not primary: a hand is what this screen is for, and
            // closing is what it offers when the user would rather not play.
            PixelButton(
                text = "✕  CLOSE APP",
                onClick = onClose,
                style = PixelButtonStyle.SECONDARY,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
        }
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
    onClose: () -> Unit,
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

                    // The hole card keeps its slot. Drawn face down while the hand is live,
                    // then the *same* composable turns over when the dealer's hand arrives.
                    // Composing the back and the revealed card at two different call sites
                    // disposes one and builds the other, and the turn-over is never seen —
                    // the reveal reads as a swap. One call site with a changing [faceDown]
                    // is what makes it a flip.
                    val hole = state.dealerFull?.cards?.getOrNull(1)
                    PlayingCard(
                        card = hole ?: Card(Rank.TWO, Suit.SPADES),
                        faceDown = hole == null,
                        dealDelayMillis = PixelMotion.DealStaggerMillis,
                    )

                    // The dealer's draw, dealt behind the turn-over.
                    state.dealerFull?.cards?.drop(2)?.forEachIndexed { index, card ->
                        PlayingCard(
                            card,
                            faceDown = false,
                            dealDelayMillis = (index + 2) * PixelMotion.DealStaggerMillis,
                        )
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
                state.playerHand?.cards?.forEachIndexed { index, card ->
                    // Deal order is dealer-first, so the opening pair lands two cards behind
                    // the dealer's. A card drawn later is the only new card on the table and
                    // has nothing to follow, so it lands at once — a HIT has to feel like a
                    // HIT, not like a queue.
                    PlayingCard(
                        card,
                        faceDown = false,
                        dealDelayMillis = if (index < 2) {
                            (index + 2) * PixelMotion.DealStaggerMillis
                        } else {
                            0L
                        },
                    )
                }
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

            // A lost hand has an exit, and it sits directly under the retry rather than at
            // the bottom of the panel. Losing is the moment the debt just grew, and the
            // only other way out was past the timer, below the fold on a short screen —
            // so the cheapest-looking next move was always another hand. This is the same
            // act as CLOSE APP and grants nothing: it stops the bleeding and takes the
            // loss already on the books. A won hand has no debt to stop, so it gets no
            // such offer — CONTINUE is already the way forward.
            if (!won) {
                Spacer(Modifier.height(PixelSpace.Snug))
                PixelButton(
                    text = "✕  STOP — TAKE THE LOSS",
                    onClick = onClose,
                    style = PixelButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
 *  have their own line and never clip out of the card frame.
 *
 *  Two motions ride on the card, both stepped per §18:
 *
 *  - **Deal.** The card drops its own height and fades up over [PixelMotion.DealMillis]
 *    when it first composes, [dealDelayMillis] late. The delay is how a hand arrives card
 *    by card instead of as one block; the card's slot is reserved from the first frame, so
 *    the row never reflows while a card is still on its way down.
 *  - **Flip.** [faceDown] turning false turns the card over on `rotationY` through four
 *    hard frames and swaps which face is drawn at the halfway point — a card caught
 *    edge-on, not a crossfade. This is the dealer's reveal, and it is the only reason the
 *    hole card is one call site rather than two.
 *
 *  The description follows [faceDown], which is the truth of the card even while it is
 *  still mid-turn: TalkBack must never read a back that is on its way to being a face.
 */
@Composable
fun PlayingCard(
    card: Rank,
    suit: Suit,
    faceDown: Boolean,
    /** Stagger before this card lands; 0 for a card dealt on its own. */
    dealDelayMillis: Long = 0L,
) {
    val suitColor =
        if (suit == Suit.HEARTS || suit == Suit.DIAMONDS) PixelPalette.Danger else PixelPalette.CardInk

    // 0 = flat on the table, 1 = landed. Kept as an Animatable rather than a plain
    // animateFloatAsState because the drop has to be held at 0 for the stagger — a
    // state-driven float would jump straight to its target and land the card early.
    val entry = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (dealDelayMillis > 0L) delay(dealDelayMillis)
        entry.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = PixelMotion.DealMillis,
                easing = PixelMotion.Stepped,
            ),
        )
    }

    val flip by animateFloatAsState(
        targetValue = if (faceDown) 180f else 0f,
        animationSpec = tween(
            durationMillis = PixelMotion.FlipMillis,
            easing = PixelMotion.Stepped,
        ),
        label = "playingCardFlip",
    )

    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 74.dp)
            .offset(y = PixelSpace.Room * (1f - entry.value))
            .alpha(entry.value)
            .graphicsLayer {
                rotationY = flip
                // The default camera distance is 8dp from a card that is only 52dp wide,
                // which is a lens pressed to the table: the flip distorts hard enough to
                // read as a different card. Pushing it out keeps the turn flat and hard.
                cameraDistance = 24f * density
            }
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
        // Past the halfway point the box has turned its back to the viewer, so that is
        // where the back face belongs — and it is drawn counter-rotated, or a card at
        // 180° would show a mirrored motif and read as a printing error.
        if (flip > 90f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f },
                contentAlignment = Alignment.Center,
            ) {
                // Tiny Sonder motif on the back: crescent + S (§9: cat/crescent/S sprite).
                Text(
                    text = "☾S",
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = PixelPalette.Bg,
                )
            }
        } else {
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
        }
    }
}

/** Overload for the domain Card type (avoids name collision). */
@Composable
fun PlayingCard(
    card: Card,
    faceDown: Boolean,
    dealDelayMillis: Long = 0L,
) {
    PlayingCard(card.rank, card.suit, faceDown, dealDelayMillis)
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
