package com.example.sonder.ui.gate

import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.di.ApplicationScope
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.BlackjackRules
import com.example.sonder.domain.DealtHand
import com.example.sonder.domain.model.Card
import com.example.sonder.domain.model.Hand
import com.example.sonder.domain.model.HandOutcome
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One round of table state visible to the gate UI. */
data class TableState(
    val targetPackage: String = "",
    val phase: Phase = Phase.IDLE,
    val playerHand: Hand? = null,
    val dealerUp: Hand? = null,
    val dealerFull: Hand? = null, // revealed after stand
    /**
     * The bet this hand is played for, as the table resolved it. Zero means no chip has been
     * chosen yet — there is no default bet, so the table opens with nothing on it and the
     * DEAL control asks for a chip rather than dealing a hand nobody agreed to.
     */
    val stakeMillis: Long = 0L,
    /** Whether the selected bet is ALL IN, which follows the bank rather than a chip. */
    val allIn: Boolean = false,
    /** Unspent access this app has earned; the whole of what "granted" means. */
    val bankMillis: Long = 0L,
    /** The app's ceiling, which a win can never take the bank past. */
    val maxMillis: Long = AccessPolicy.DEFAULT_MAX_MILLIS,
    val message: String = "",
    val showResult: Boolean = false,
    val lastOutcome: HandOutcome? = null,
    /** The bank the last settled hand left behind; the result line reads it, not the live one. */
    val lastBankAfterMillis: Long = 0L,
) {
    enum class Phase { IDLE, DEALING, PLAYER_TURN, DEALER_TURN, RESOLVED }

    /** Nothing left to spend, whatever the table is doing. */
    val outOfTime: Boolean get() = bankMillis <= 0L

    /**
     * Whether the hand on the table was won, which is the only thing that opens the app.
     *
     * Keyed to the *outcome* rather than to the bank, because a push leaves the bank exactly
     * as it was: a table that read "there is time in the bank, come in" would hand a free
     * entry to a hand nobody won, which is the whole of what this gate exists to prevent.
     */
    val justWon: Boolean get() =
        showResult && lastOutcome == HandOutcome.WIN && lastBankAfterMillis > 0L

    /**
     * Whether the app is done for the day: the bank is spent and the table is at rest.
     *
     * The phase matters. A hand settled against an emptied bank — the bet took the lot —
     * still has a result to show, and replacing it with the wall mid-session would swallow
     * the one piece of feedback the player just played for. The wall belongs to the moment
     * the user comes back to an app with nothing in it, which is exactly `IDLE`.
     */
    val lockedOut: Boolean get() = outOfTime && phase == Phase.IDLE

    /** Whether a chip has been chosen at all. No chip, no bet, and DEAL has nothing to deal. */
    val hasStake: Boolean get() = stakeMillis > 0L

    /**
     * Whether the selected bet is one the table can deal: a chip the bank covers. The bank
     * backs the bet without exception, so an empty bank deals nothing.
     */
    val stakePlayable: Boolean get() = AccessPolicy.canStake(bankMillis, stakeMillis)
}

/**
 * The blackjack gate's state machine, owned by the process (not an Activity) so
 * the blocker can live in a service-hosted overlay window. Previously this logic
 * sat in a ViewModel behind BlockActivity, which dragged the blocker into the
 * detox app's task.
 *
 * The economy it plays is a per-app bank of access time ([AccessPolicy]). A hand is played
 * for a stake the player picks from the fixed chips, or for the whole bank; a win credits the
 * stake up to the app's ceiling, a loss debits it down to nothing, and a push moves nothing.
 * The bank is only ever spent by using the app, so the panel is what the player sees when it
 * has run out — and the hand is the way back in.
 */
@Singleton
class GateController @Inject constructor(
    private val repository: EnforcementRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TableState())
    val state: StateFlow<TableState> = _state

    /** Emits the package when the user earns access and dismisses the blocker. */
    private val _unlocked = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val unlocked: SharedFlow<String> = _unlocked

    /**
     * Emits the package when the user asks to leave the blocked app entirely.
     *
     * Separate from [unlocked] because the two are not the same act and must not be
     * conflated by anything downstream: one says "the user earned time here", the other says
     * "the user wants out". Handling the second as the first would hand out access as a
     * reward for closing the app.
     *
     * The controller only announces the request. Nothing here knows how to leave an app —
     * that is a service capability — so the coordinator, which owns the service, does the
     * work. See EnforcementCoordinator.startCloseRequests.
     */
    private val _closeRequested = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val closeRequested: SharedFlow<String> = _closeRequested

    private var deck: List<Card> = emptyList()
    private var dealt: DealtHand? = null

    /** Point the table at a target. Re-entering the same app keeps an in-progress hand. */
    fun begin(targetPackage: String) {
        if (_state.value.targetPackage == targetPackage && _state.value.phase != TableState.Phase.IDLE) {
            // Same target, hand in progress: keep the hands, but the bank can have moved
            // underneath — a hand settled elsewhere, the day rolled over, time drained by a
            // re-check pass — so it is re-read rather than left stale.
            scope.launch { refreshBank() }
            return
        }
        // A reused gate can retarget to a different package, so a fresh start must
        // reinitialise rather than keep the previous target's hands and rules.
        deck = emptyList()
        dealt = null
        _state.value = TableState(targetPackage = targetPackage)
        scope.launch { refreshBank() }
    }

    /**
     * Re-read the app's bank and ceiling, and re-resolve the bet against them.
     *
     * Called on start and after every settled hand, since a hand moves the bank and a bank of
     * nothing changes what the bets mean. An ALL IN bet is re-pointed at the new balance, and
     * is dropped outright when there is no balance left to be all in with.
     *
     * A chip bet is left exactly where the player put it, and is only ever cleared — never
     * swapped for another chip. The bank shrinking under a chosen chip is not a reason to
     * place a smaller bet on the player's behalf: it is money they did not agree to put up,
     * and the table's job is to say the bet no longer fits (the readout under the row does)
     * rather than to silently re-bet for them.
     */
    private suspend fun refreshBank() {
        val pkg = _state.value.targetPackage
        if (pkg.isEmpty()) return
        val bank = repository.cachedRemaining(pkg)
        val max = repository.cachedMaxMillis(pkg)
        val current = _state.value
        val keepAllIn = current.allIn && bank > 0L
        val stake = when {
            keepAllIn -> bank
            current.allIn -> 0L
            !AccessPolicy.canStake(bank, current.stakeMillis) -> 0L
            else -> current.stakeMillis
        }
        _state.value = current.copy(
            bankMillis = bank,
            maxMillis = max,
            allIn = keepAllIn,
            stakeMillis = stake,
        )
    }

    /**
     * Choose the bet: a chip at face value, or the whole bank.
     *
     * A chip the bank does not cover is refused rather than clamped. The bet is drawn on the
     * bank, and a stake drawn on time that is not there would be paid out of nothing on a win
     * — the table's chips are greyed for the same reason, and this is the same rule kept
     * where a tap cannot get around it. The smallest chip is the exception the rule itself
     * carries ([AccessPolicy.canStake]), so an empty bank can still sit down to a hand.
     */
    fun stake(chipMillis: Long) {
        if (chipMillis !in AccessPolicy.CHIPS) return
        val current = _state.value
        if (current.phase != TableState.Phase.IDLE && current.phase != TableState.Phase.RESOLVED) return
        if (!AccessPolicy.canStake(current.bankMillis, chipMillis)) return
        _state.value = current.copy(stakeMillis = chipMillis, allIn = false)
    }

    /** Bet the whole bank. Refused at nothing, where there is no bank to go all in with. */
    fun allIn() {
        val current = _state.value
        if (current.phase != TableState.Phase.IDLE && current.phase != TableState.Phase.RESOLVED) return
        if (current.bankMillis <= 0L) return
        _state.value = current.copy(stakeMillis = current.bankMillis, allIn = true)
    }

    fun deal() {
        val pkg = _state.value.targetPackage
        if (pkg.isEmpty()) return
        // A bet is only placed from a resting table. Mid-hand the stake is already committed
        // to the cards on the felt, and re-dealing over them would take the old hand's stake
        // off the books without ever settling it.
        val phase = _state.value.phase
        if (phase != TableState.Phase.IDLE && phase != TableState.Phase.RESOLVED) return
        // A hand is dealt for a bet the table can carry: a chip the bank backs. No chip at
        // all is not a bet either — the table opens with nothing selected on purpose, and
        // dealing one for the user would place a wager they never chose.
        if (!_state.value.stakePlayable) return

        deck = BlackjackRules.shuffledDeck()
        dealt = BlackjackRules.deal(deck)
        deck = dealt!!.remainingDeck

        _state.value = _state.value.copy(
            phase = TableState.Phase.PLAYER_TURN,
            playerHand = dealt!!.player,
            dealerUp = dealt!!.dealerUp,
            dealerFull = null,
            showResult = false,
            lastOutcome = null,
            message = "HIT OR STAND?",
        )
    }

    fun hit() {
        val current = _state.value
        val hand = current.playerHand ?: return
        if (current.phase != TableState.Phase.PLAYER_TURN) return

        val (card, rest) = BlackjackRules.draw(deck)
        deck = rest
        val newHand = Hand(hand.cards + card)

        if (newHand.isBust) {
            _state.value = current.copy(
                playerHand = newHand,
                phase = TableState.Phase.RESOLVED,
                message = "BUST — HOUSE TAKES IT",
            )
            // A bust never sees the dealer's hole card, so the history records only the
            // hand that busted — the ledger must not show a card this hand never revealed.
            settle(current.targetPackage, HandOutcome.LOSE, newHand, null)
        } else if (newHand.total == 21) {
            _state.value = current.copy(playerHand = newHand, phase = TableState.Phase.DEALER_TURN)
            standInternal(current.targetPackage, dealt ?: return)
        } else {
            _state.value = current.copy(playerHand = newHand, message = "HIT OR STAND?")
        }
    }

    fun stand() {
        val current = _state.value
        if (current.phase != TableState.Phase.PLAYER_TURN) return
        // The hand and its deck are captured here rather than read back after the dealer
        // beat. The table can be retargeted inside those 600 ms — Home, then a second
        // blocked app — and the live fields would by then belong to the new app's hand,
        // so the old hand's outcome would land on a package the player never played.
        val dealtHand = dealt ?: return
        _state.value = current.copy(phase = TableState.Phase.DEALER_TURN)
        standInternal(current.targetPackage, dealtHand)
    }

    fun playAgain() {
        // "Play again" rests the table without leaving the blocker. The bank is re-read rather
        // than kept: the hand that just settled is the thing that moved it.
        val current = _state.value
        _state.value = current.copy(
            phase = TableState.Phase.IDLE,
            playerHand = null,
            dealerUp = null,
            dealerFull = null,
            showResult = false,
            lastOutcome = null,
            message = "",
        )
        scope.launch { refreshBank() }
    }

    /** User earned access: release the gate and reset the table for next time. */
    fun releaseAccess() {
        val pkg = _state.value.targetPackage
        if (pkg.isEmpty()) return
        _state.value = TableState(targetPackage = pkg)
        deck = emptyList()
        dealt = null
        _unlocked.tryEmit(pkg)
    }

    /**
     * User wants out of the blocked app rather than a hand.
     *
     * Grants nothing: the access a win would have produced is exactly what this must not hand
     * out, or closing the blocker would be the cheapest way to open the app. The table is
     * reset so a later visit starts clean, and the request goes out for the coordinator to act
     * on.
     */
    fun closeApp() {
        val pkg = _state.value.targetPackage
        if (pkg.isEmpty()) return
        _state.value = TableState(targetPackage = pkg)
        deck = emptyList()
        dealt = null
        _closeRequested.tryEmit(pkg)
    }

    private fun standInternal(pkg: String, dealtHand: DealtHand) {
        scope.launch {
            delay(600) // 2-frame dealer reveal beat
            // A retarget inside the beat means this hand is no longer the table's. The
            // dealer would otherwise be dealt from the new app's deck and the outcome
            // recorded against it.
            if (_state.value.targetPackage != pkg) return@launch

            val playerHand = _state.value.playerHand ?: return@launch

            val dealerResult = BlackjackRules.dealerPlay(
                dealtHand.dealerUp,
                dealtHand.dealerHole,
                dealtHand.remainingDeck,
            )
            val playerNatural = playerHand.isNatural
            val dealerNatural = dealerResult.dealer.isNatural && playerHand.cards.size == 2

            val outcome = when {
                playerNatural && dealerNatural -> HandOutcome.PUSH
                playerNatural -> HandOutcome.WIN
                dealerNatural -> HandOutcome.LOSE
                else -> BlackjackRules.settle(playerHand, dealerResult.dealer)
            }

            _state.value = _state.value.copy(
                dealerFull = dealerResult.dealer,
                phase = TableState.Phase.RESOLVED,
                message = when (outcome) {
                    HandOutcome.WIN -> "YOU WIN"
                    HandOutcome.LOSE -> "YOU LOSE"
                    HandOutcome.PUSH -> "PUSH — FREE REPLAY"
                },
            )
            settle(pkg, outcome, playerHand, dealerResult.dealer)
        }
    }

    /**
     * Persist a finished hand against the package it was played on, then show its result.
     *
     * The hand is recorded whatever the table is doing by now, because it was played; only
     * the panel is conditional. A table that has moved on — retargeted to another app, or
     * reset by PLAY AGAIN while the result was still being written — must not have a result
     * written onto it, which is how an IDLE table once grew a result row for a hand the
     * player had already dismissed.
     *
     * The two hands travel with it because this is the last moment they exist: the table
     * resets on the next deal, and the ledger is the only place a finished hand can be read
     * back from. They are captured in memory, never re-derived from the deck.
     *
     * The stake is read from the table at the moment of settlement, not from the state that
     * comes back: the bet was placed when the hand was dealt, and a bet changed while the
     * dealer was playing would settle the hand for an amount nobody agreed to.
     */
    private fun settle(pkg: String, outcome: HandOutcome, playerHand: Hand?, dealerHand: Hand?) {
        val stake = _state.value.stakeMillis
        scope.launch {
            val bankAfter = repository.onHandResult(
                packageName = pkg,
                outcome = outcome,
                stakeMillis = stake,
                playerHand = playerHand,
                dealerHand = dealerHand,
            )
            if (_state.value.targetPackage == pkg && _state.value.phase == TableState.Phase.RESOLVED) {
                _state.value = _state.value.copy(
                    showResult = true,
                    lastOutcome = outcome,
                    lastBankAfterMillis = bankAfter,
                )
                // Re-reads the bank the hand just moved, and re-points an ALL IN bet at it.
                refreshBank()
            }
        }
    }
}
