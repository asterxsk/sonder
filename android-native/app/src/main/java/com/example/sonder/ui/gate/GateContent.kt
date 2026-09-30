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
import com.example.sonder.domain.AccessPolicy
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

/**
 * The blocker surface (design_v3 §10–12), hosted either inside the service-owned
 * system overlay window or, for previews, any Compose host. Stateless: all state
 * and actions come from the caller, so it can live outside an Activity.
 *
 * There is one mode, because there is one state the blocker is ever raised in: the app has
 * nothing left in its bank. The table is the way back in, and CLOSE is the way out.
 *
 * CLOSE sits below the table, rendered once here rather than inside it. It is the way *out* —
 * the way in is a hand — and it is the only control this screen has that is always present,
 * which is the point: §10 through §12 never specify one, and without it a blocked app is a
 * wall the user has to know to press Back or Home to escape, which inside an app that
 * intercepts Back is not an escape at all. It renders in every state: the way out is never
 * something the user has to earn or outlast.
 */
@Composable
fun GateContent(
    label: String,
    state: TableState,
    onDeal: () -> Unit,
    onStake: (Long) -> Unit,
    onAllIn: () -> Unit,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onAccessGranted: () -> Unit,
    onPlayAgain: () -> Unit,
    onClose: () -> Unit,
) {
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

            BlackjackBlocker(
                label = label,
                state = state,
                onDeal = onDeal,
                onStake = onStake,
                onAllIn = onAllIn,
                onHit = onHit,
                onStand = onStand,
                onAccessGranted = onAccessGranted,
                onPlayAgain = onPlayAgain,
                onClose = onClose,
            )

            Spacer(Modifier.height(12.dp))

            // The way out, below the table's own controls so it never competes with the way in.
            // Secondary, not primary: a hand is what this screen is for, and closing is what it
            // offers when the user would rather not play.
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
private fun BlackjackBlocker(
    label: String,
    state: TableState,
    onDeal: () -> Unit,
    onStake: (Long) -> Unit,
    onAllIn: () -> Unit,
    onHit: () -> Unit,
    onStand: () -> Unit,
    onAccessGranted: () -> Unit,
    onPlayAgain: () -> Unit,
    onClose: () -> Unit,
) {
    // §14 status badge row. Played from the bank the *table* is holding rather than from the
    // last outcome, so a settled win whose time has already been spent by a re-check pass
    // cannot keep a GRANTED badge over an app that is once again empty.
    when {
        state.showResult && state.lastBankAfterMillis > 0L ->
            PixelStatusBadge(BadgeTone.GRANTED)
        state.outOfTime ->
            PixelStatusBadge(BadgeTone.COOLDOWN, labelOverride = "OUT OF TIME")
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
                text = if (state.outOfTime) {
                    // Nothing to bet and nothing to win with: the day's opening stake is what
                    // this screen is waiting for, so it names when that arrives rather than
                    // inviting a hand the table will not deal.
                    "OUT OF TIME — THE BANK OPENS AGAIN AT MIDNIGHT"
                } else {
                    "BANK ${formatRemaining(state.bankMillis)} OF ${formatRemaining(state.maxMillis)}"
                },
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

    // Controls: IDLE → chips + DEAL, PLAYER_TURN → HIT/STAND, RESOLVED → CONTINUE, with
    // PLAY AGAIN beside it whenever the bank can cover one.
    when (state.phase) {
        TableState.Phase.IDLE -> {
            StakeChooser(
                state = state,
                onStake = onStake,
                onAllIn = onAllIn,
                enabled = true,
            )
            Spacer(Modifier.height(PixelSpace.Snug))
            PixelButton(
                text = "♠  DEAL HAND",
                onClick = onDeal,
                enabled = state.stakeBacked,
                modifier = Modifier.fillMaxWidth(),
            )
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
            // CONTINUE is the way in and is offered only when there is something to spend,
            // which is what the badge above is also keyed to. A hand that left the bank empty
            // must not offer a way into an app the coordinator is about to cover again.
            if (state.canPlayAgain) {
                PixelButton(
                    text = "CONTINUE →",
                    onClick = onAccessGranted,
                    style = PixelButtonStyle.SUCCESS,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(PixelSpace.Snug))
                // PLAY AGAIN sits under CONTINUE rather than replacing it: the time won is
                // already banked, so the player chooses between spending it and betting it
                // back for more up to the ceiling.
                StakeChooser(
                    state = state,
                    onStake = onStake,
                    onAllIn = onAllIn,
                    enabled = true,
                )
                Spacer(Modifier.height(PixelSpace.Snug))
                PixelButton(
                    text = "♠  PLAY AGAIN",
                    onClick = onDeal,
                    style = PixelButtonStyle.SECONDARY,
                    enabled = state.stakeBacked,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // The hand took the lot. Nothing is left to spend and nothing is left to bet,
                // so the chips come up greyed and this is the wall the day opens from: the
                // stake it opens with is the next chip the player has.
                StakeChooser(
                    state = state,
                    onStake = onStake,
                    onAllIn = onAllIn,
                    enabled = true,
                )
                Spacer(Modifier.height(PixelSpace.Snug))
                PixelButton(
                    text = "♠  PLAY AGAIN",
                    onClick = onDeal,
                    enabled = state.stakeBacked,
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
    }

/**
 * The chips: three fixed bets plus the whole bank.
 *
 * A chip is a bet drawn on the bank, so a chip the bank cannot cover is greyed: there is no
 * time behind it, and the win it would pay out would be time invented rather than won. ALL IN
 * greys out with them, since it is the whole bank and there is no bank to put in.
 *
 * When every chip is greyed there is nothing to bet and nothing to deal, and the line under
 * the row says so — the table is waiting for the day's opening stake rather than for a
 * decision the player cannot improve on.
 *
 * The selected bet is the filled chip. A chip that is not selected is still pressable, as long
 * as the bank covers it.
 */
@Composable
private fun StakeChooser(
    state: TableState,
    onStake: (Long) -> Unit,
    onAllIn: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccessPolicy.CHIPS.forEach { chip ->
            val selected = !state.allIn && state.stakeMillis == chip
            PixelButton(
                text = formatRemaining(chip),
                onClick = { onStake(chip) },
                style = if (selected) PixelButtonStyle.PRIMARY else PixelButtonStyle.SECONDARY,
                // Backed by the bank, or nothing: a bet is played with time the player holds.
                enabled = enabled && AccessPolicy.canStake(state.bankMillis, chip),
                modifier = Modifier.weight(1f),
            )
        }
        PixelButton(
            text = "ALL IN",
            onClick = onAllIn,
            style = if (state.allIn) PixelButtonStyle.PRIMARY else PixelButtonStyle.SECONDARY,
            // Disabled at nothing: there is no bank to be all in with. It is the one chip that
            // stops being affordable above zero, since it is whatever the bank holds.
            enabled = enabled && !state.outOfTime,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = if (state.stakeBacked) {
            "BET ${formatRemaining(state.stakeMillis)}  ·  BANK ${formatRemaining(state.bankMillis)}"
        } else {
            // The bet is unaffordable in every denomination, so the readout is replaced by the
            // one thing that changes it. Two reasons, and they read differently: an empty bank
            // is out of time, and a bank too thin for the smallest chip is too little to bet.
            if (state.outOfTime) {
                "OUT OF TIME — THE BANK OPENS AGAIN AT MIDNIGHT"
            } else {
                "TOO LITTLE TO BET — THE BANK OPENS AGAIN AT MIDNIGHT"
            }
        },
        style = MonoTypeScale.Metadata,
        color = PixelPalette.Muted,
    )
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
