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
import java.time.ZoneId
import java.util.Locale
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
import com.example.sonder.ui.kit.ChipIcon
import com.example.sonder.ui.kit.ChipState
import com.example.sonder.ui.kit.ChipTier
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.PixelTimer
import com.example.sonder.ui.kit.TimerTone

/**
 * The gate surface (design_v3 §10–12), hosted either inside the service-owned system
 * overlay window or, for previews, any Compose host. Stateless: all state and actions come
 * from the caller, so it can live outside an Activity.
 *
 * It has two faces, and which one is drawn is the whole of the difference between them:
 *
 *  - **The table**, while the bank still holds time. A won hand is the way in, and the bank
 *    is spent by using the app rather than by winning. Nothing is dealt until a chip is
 *    chosen, so the bet is always one the player actually placed.
 *  - **The wall**, when the bank is spent. There is no table and nothing to play for: the
 *    day is done, and the only controls are the countdown to the refill and the way out.
 *
 * CLOSE sits below either, rendered once here rather than inside the table. It is the way
 * *out* — the way in is a hand — and it is the only control this screen has that is always
 * present, which is the point: §10 through §12 never specify one, and without it a gated app
 * is a wall the user has to know to press Back or Home to escape, which inside an app that
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
    // screen a top-anchored one leaves the whole lower half of the gate empty. The
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

            if (state.lockedOut) {
                LockedBlocker(label = label)
            } else {
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
            }

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

/**
 * The wall: the bank is spent, so there is nothing to stake and nothing to win.
 *
 * It counts down to the next refill rather than to nothing, because "come back tomorrow" with
 * no number on it reads as a wall with no door. The countdown is derived from the clock
 * through [AccessPolicy.nextLocalMidnight] rather than from a stored deadline, so it cannot
 * be stale after a process death, a reboot, or a timezone change — the same reason the refill
 * itself is a read predicate and not an alarm.
 *
 * There is no chip row here even greyed out. A control that exists only to say "no" invites
 * the tap that finds out; the absence of the table is the message, and the countdown says how
 * long it lasts.
 */
@Composable
private fun LockedBlocker(label: String) {
    PixelStatusBadge(BadgeTone.LOCKED)

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
                text = "OUT OF TIME — LOCKED FOR TODAY",
                style = MonoTypeScale.Metadata,
                color = PixelPalette.Danger,
            )
        }
    }

    Spacer(Modifier.height(16.dp))

    RefillCountdown()
}

/**
 * Time until the bank is refilled, ticking once a second.
 *
 * [nowMillis] is state, not a clock read inside the composition, so the digits change when
 * the tick changes them and not on every unrelated recomposition. The tick is a plain
 * coroutine — the same cadence [PixelHoldButton] serves a hold on — rather than an animated
 * float, because this is a clock and not a motion: §18's quantisation governs interpolation,
 * and a countdown has nothing to interpolate.
 */
@Composable
private fun RefillCountdown(
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
) {
    var now by remember(nowMillis) { mutableLongStateOf(nowMillis) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }

    // Floored at zero: in the second between midnight passing and the tick that notices, the
    // subtraction goes negative, and a countdown must never render as time owing.
    val left = (AccessPolicy.nextLocalMidnight(now, zoneId) - now).coerceAtLeast(0L)
    val totalSeconds = left / 1_000L

    PixelTimer(
        timeText = String.format(
            Locale.ROOT,
            "%02d:%02d:%02d",
            totalSeconds / 3_600,
            (totalSeconds % 3_600) / 60,
            totalSeconds % 60,
        ),
        caption = "UNTIL YOUR BANK REFILLS",
        tone = TimerTone.LOCKED,
    )
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
    // §14 status badge row. GRANTED is the badge of a hand that was won, and it is keyed to
    // the win rather than to the bank the hand left: a push leaves time in the bank without
    // granting anything, so a balance-keyed badge would read GRANTED over the one outcome
    // that must not open a door. The wall is drawn instead of this whole composable when the
    // bank is gone, so there is no third case here.
    if (state.justWon) {
        PixelStatusBadge(BadgeTone.GRANTED)
    } else {
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
                text = "BANK ${formatRemaining(state.bankMillis)} OF ${formatRemaining(state.maxMillis)}",
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
            DealControl(state = state, onDeal = onDeal, text = "♠  DEAL HAND")
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
            // CONTINUE is the way in, and only a won hand has one. A push leaves the bank
            // exactly as it was, so a table that keyed this to the bank would open the app
            // on a hand nobody won — and a loss leaves time in the bank too, which is why
            // the gate above is keyed to the outcome rather than to the balance.
            if (state.justWon) {
                PixelButton(
                    text = "CONTINUE →",
                    onClick = onAccessGranted,
                    style = PixelButtonStyle.SUCCESS,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(PixelSpace.Snug))
            }
            // PLAY AGAIN sits under CONTINUE rather than replacing it: the time won is
            // already banked, so the player chooses between spending it and betting it back
            // for more up to the ceiling. It is offered whether or not the hand was won, and
            // whether or not the bank survived it — betting the rest back up is the point of
            // a table, and the chips say for themselves which bets the bank can still cover.
            StakeChooser(
                state = state,
                onStake = onStake,
                onAllIn = onAllIn,
                enabled = true,
            )
            Spacer(Modifier.height(PixelSpace.Snug))
            DealControl(state = state, onDeal = onDeal, text = "♠  PLAY AGAIN")
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
 * The deal control, which is also the prompt when there is nothing to deal.
 *
 * With no chip chosen there is no bet and nothing to deal, so the button does not sit greyed
 * out waiting to be understood — it says what it is waiting for, in the palette's red, and
 * stops being a button at all. It becomes a control the moment a chip is on the table, and
 * then it only greys out in the one case the readout under the chips already explains: a bet
 * the bank has shrunk below.
 *
 * [text] is the resting label — DEAL HAND at an opening table, PLAY AGAIN after a settled
 * hand — so the same control serves both without the caller having to remember which.
 */
@Composable
private fun DealControl(
    state: TableState,
    onDeal: () -> Unit,
    text: String,
) {
    if (state.hasStake) {
        PixelButton(
            text = text,
            onClick = onDeal,
            enabled = state.stakePlayable,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        // Disabled, not wired to nothing: it is a prompt rather than a control, and the
        // DANGER tetrad draws exactly that — a red frame that reads as a stop rather than as
        // an action. TalkBack gets the same sentence the eye does.
        PixelButton(
            text = "PLEASE SELECT A CHIP",
            onClick = {},
            style = PixelButtonStyle.DANGER,
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The chips: three fixed bets plus the whole bank.
 *
 * A chip is a bet drawn on the bank, so a chip the bank cannot cover is greyed: there is no
 * time behind it, and the win it would pay out would be time invented rather than won. There
 * is no exception to that, and none of the chips is selected on the table's arrival — the bet
 * is the player's to place, so the table opens with an empty felt and the deal control asking
 * for a chip.
 *
 * Each stake carries its own chip art, one hue per denomination, so the row is read as four
 * different bets rather than the same button four times at four prices. The chip is drawn in
 * the state the stake is in — affordable, chosen, or unaffordable — which is the same
 * three-way split the button underneath it is painted in, so the two never disagree.
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
            val affordable = enabled && AccessPolicy.canStake(state.bankMillis, chip)
            val tier = ChipTier.forStake(chip)
            PixelButton(
                // The stake in words rather than as a clock. "2 MIN" says what the chip buys;
                // "02:00" beside a chip that is already the 2:00 bet says the same thing twice
                // in the notation the bank readout below is using for something else.
                text = tier.label,
                onClick = { onStake(chip) },
                style = if (selected) PixelButtonStyle.PRIMARY else PixelButtonStyle.SECONDARY,
                // Backed by the bank, or the table's own smallest chip: a bet is played with
                // time the player holds, and the smallest one with the seat.
                enabled = affordable,
                modifier = Modifier.weight(1f),
                // Four chips share one screen, so each button is about 76dp across and the
                // default 16dp gutter would leave 44dp for a label that needs more. The
                // padding comes off the sides rather than the label shrinking, because the
                // label is the thing being read.
                horizontalPadding = 4.dp,
                leading = {
                    ChipIcon(tier = tier, state = chipState(selected, affordable), size = 34.dp)
                },
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
            horizontalPadding = 4.dp,
            leading = {
                ChipIcon(
                    tier = ChipTier.HIGH,
                    state = chipState(state.allIn, enabled && !state.outOfTime),
                    size = 34.dp,
                )
            },
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = when {
            !state.hasStake ->
                "BANK ${formatRemaining(state.bankMillis)}  ·  PICK A CHIP TO PLAY"
            state.stakePlayable ->
                "BET ${formatRemaining(state.stakeMillis)}  ·  BANK ${formatRemaining(state.bankMillis)}"
            else ->
                // The selected bet outruns the bank. The table re-reads the bet whenever the
                // bank moves, so this is a momentary disagreement — and the readout names the
                // thing that resolves it rather than leaving a greyed chip unexplained.
                "BET TOO BIG FOR THE BANK — PICK A SMALLER CHIP"
        },
        style = MonoTypeScale.Metadata,
        color = PixelPalette.Muted,
    )
}

/**
 * The state a stake's chip is drawn in.
 *
 * Chosen beats affordable: a chip can be selected and simultaneously too big for the bank —
 * the table re-resolves the bet when the bank moves, so for a moment the two disagree — and
 * the chip has to show what the user picked rather than what the bank holds. The "bet too big"
 * line under the row is what says the pick is unplayable.
 */
private fun chipState(selected: Boolean, affordable: Boolean): ChipState = when {
    selected -> ChipState.SELECTED
    affordable -> ChipState.READY
    else -> ChipState.DEAD
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
