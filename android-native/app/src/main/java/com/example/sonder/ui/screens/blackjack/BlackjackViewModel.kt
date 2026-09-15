package com.example.sonder.ui.screens.blackjack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.domain.BlackjackRules
import com.example.sonder.domain.DealtHand
import com.example.sonder.domain.model.Card
import com.example.sonder.domain.model.Hand
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.Rank
import com.example.sonder.domain.model.Suit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** One round of table state visible to the UI. */
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

@HiltViewModel
class BlackjackViewModel @Inject constructor(
    private val repository: EnforcementRepository,
    private val expiryScheduler: com.example.sonder.platform.scheduling.GrantExpiryScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(TableState())
    val state: StateFlow<TableState> = _state

    private var deck: List<Card> = emptyList()
    private var dealt: DealtHand? = null

    fun start(targetPackage: String) {
        if (_state.value.targetPackage == targetPackage && _state.value.phase != TableState.Phase.IDLE) return
        viewModelScope.launch {
            val debt = repository.currentDebt(targetPackage)
            _state.value = TableState(targetPackage = targetPackage, debtMinutes = debt / 60_000)
        }
    }

    fun deal() {
        val pkg = _state.value.targetPackage
        if (pkg.isEmpty()) return
        deck = BlackjackRules.shuffledDeck()
        dealt = BlackjackRules.deal(deck)
        deck = dealt!!.remainingDeck

        val player = dealt!!.player
        val dealerUp = dealt!!.dealerUp

        // Natural check happens only when the player stands on the natural;
        // the player may still draw to 21 (no bust risk at 21).
        _state.value = _state.value.copy(
            phase = TableState.Phase.PLAYER_TURN,
            playerHand = player,
            dealerUp = dealerUp,
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

    private fun standInternal() {
        val d = dealt ?: return
        viewModelScope.launch {
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
        viewModelScope.launch {
            val grantedUntil = repository.onHandResult(pkg, outcome)
            if (grantedUntil != null) {
                expiryScheduler.scheduleExpiry(pkg, grantedUntil)
            }
            val debt = repository.currentDebt(pkg)
            _state.value = _state.value.copy(
                debtMinutes = debt / 60_000,
                showResult = true,
                lastOutcome = outcome,
            )
        }
    }

    fun continueAfterResult() {
        // "Play again" resets the table without leaving the gate.
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
}
