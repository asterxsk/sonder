package com.example.sonder.ui.gate

import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.di.ApplicationScope
import com.example.sonder.domain.BlackjackRules
import com.example.sonder.domain.DealtHand
import com.example.sonder.domain.model.Card
import com.example.sonder.domain.model.Hand
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.platform.scheduling.GrantExpiryScheduler
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
    val debtMinutes: Long = 0,
    val message: String = "",
    val showResult: Boolean = false,
    val lastOutcome: HandOutcome? = null,
) {
    enum class Phase { IDLE, DEALING, PLAYER_TURN, DEALER_TURN, RESOLVED }
}

/**
 * The blackjack gate's state machine, owned by the process (not an Activity) so
 * the blocker can live in a service-hosted overlay window. Previously this logic
 * sat in a ViewModel behind BlockActivity, which dragged the blocker into the
 * detox app's task.
 */
@Singleton
class GateController @Inject constructor(
    private val repository: EnforcementRepository,
    private val expiryScheduler: GrantExpiryScheduler,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(TableState())
    val state: StateFlow<TableState> = _state

    /** Emits the package when the user earns access and dismisses the blocker. */
    private val _unlocked = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val unlocked: SharedFlow<String> = _unlocked

    private var deck: List<Card> = emptyList()
    private var dealt: DealtHand? = null

    /** Point the table at a target. Re-entering the same app keeps an in-progress hand. */
    fun begin(targetPackage: String) {
        if (_state.value.targetPackage == targetPackage) return
        deck = emptyList()
        dealt = null
        _state.value = TableState(targetPackage = targetPackage)
        scope.launch {
            val debt = repository.currentDebt(targetPackage)
            if (_state.value.targetPackage == targetPackage) {
                _state.value = _state.value.copy(debtMinutes = debt / 60_000)
            }
        }
    }

    fun deal() {
        val pkg = _state.value.targetPackage
        if (pkg.isEmpty()) return
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
            settle(HandOutcome.LOSE)
        } else if (newHand.total == 21) {
            _state.value = current.copy(playerHand = newHand, phase = TableState.Phase.DEALER_TURN)
            standInternal()
        } else {
            _state.value = current.copy(playerHand = newHand, message = "HIT OR STAND?")
        }
    }

    fun stand() {
        if (_state.value.phase != TableState.Phase.PLAYER_TURN) return
        _state.value = _state.value.copy(phase = TableState.Phase.DEALER_TURN)
        standInternal()
    }

    fun playAgain() {
        // "Play again" resets the table without leaving the blocker.
        _state.value = _state.value.copy(
            phase = TableState.Phase.IDLE,
            playerHand = null,
            dealerUp = null,
            dealerFull = null,
            showResult = false,
            lastOutcome = null,
            message = "",
        )
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

    private fun standInternal() {
        val d = dealt ?: return
        scope.launch {
            delay(600) // 2-frame dealer reveal beat
            val playerHand = _state.value.playerHand ?: return@launch

            val dealerResult = BlackjackRules.dealerPlay(d.dealerUp, d.dealerHole, deck)
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
            settle(outcome)
        }
    }

    private fun settle(outcome: HandOutcome) {
        val pkg = _state.value.targetPackage
        scope.launch {
            val grantedUntil = repository.onHandResult(pkg, outcome)
            if (grantedUntil != null) {
                expiryScheduler.scheduleExpiry(pkg, grantedUntil)
            }
            val debt = repository.currentDebt(pkg)
            if (_state.value.targetPackage == pkg) {
                _state.value = _state.value.copy(
                    debtMinutes = debt / 60_000,
                    showResult = true,
                    lastOutcome = outcome,
                )
            }
        }
    }
}
