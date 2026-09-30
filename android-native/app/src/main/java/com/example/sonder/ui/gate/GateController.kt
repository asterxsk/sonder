package com.example.sonder.ui.gate

import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.di.ApplicationScope
import com.example.sonder.domain.AccessPolicy
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
    /** Effective win grant for this target: the per-app override, else the global default. */
    val winGrantMillis: Long = AccessPolicy.WIN_GRANT_MILLIS,
    /** Remaining allowance under the app's daily cap; null when the app has no cap. */
    val dailyRemainingMillis: Long? = null,
    /**
     * Whether the last settled hand actually granted access. A debt-free win grants
     * nothing once the daily cap is spent, so the RESOLVED success path must key off
     * this rather than off "won with no debt", or the gate would report CONTINUE with
     * no grant behind it and the coordinator would re-raise the gate.
     */
    val lastHandGranted: Boolean = false,
    /** True while the app is under an active DAILY_CAP lockout; the cap is not time-served. */
    val capLocked: Boolean = false,
    /**
     * Time still owed on a debt that has reached its ceiling; 0 at any lower debt.
     *
     * A hand can take the debt to the ceiling *while the table is open*, so this cannot be
     * left to the blocker's decision path: the panel has to switch to the wait-it-out state
     * the moment that happens, or the player is offered another hand at the ceiling, which
     * is the one state where hands are meant to stop.
     */
    val debtLockRemainingMillis: Long = 0,
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
        if (_state.value.targetPackage == targetPackage && _state.value.phase != TableState.Phase.IDLE) {
            // Same target, hand in progress: keep the hands, but the policy can have
            // moved underneath (a hand settled in another window, the day rolled over),
            // so the cap and the grant are re-read rather than left stale.
            scope.launch { refreshPolicy() }
            return
        }
        // A reused gate can retarget to a different package, so a fresh start must
        // reinitialise rather than keep the previous target's hands and rules.
        deck = emptyList()
        dealt = null
        _state.value = TableState(targetPackage = targetPackage)
        scope.launch { refreshPolicy() }
    }

    /**
     * Re-reads debt and the app's per-app rules so the gate shows the effective win
     * grant and today's remaining allowance under the cap. Called on start and after
     * every settled hand, since a win both changes debt and spends allowance.
     */
    private suspend fun refreshPolicy() {
        val pkg = _state.value.targetPackage
        if (pkg.isEmpty()) return
        val rules = repository.rulesFor(pkg)
        val grantedToday = repository.dailyGrantedMillis(pkg)
        val debt = repository.currentDebt(pkg)
        val cap = repository.cachedMaxDebt(pkg)
        val lockoutRemaining = repository.lockoutRemainingMillis(pkg)
        _state.value = _state.value.copy(
            debtMinutes = debt / 60_000,
            winGrantMillis = rules.winGrantMillis,
            dailyRemainingMillis = rules.dailyCapMillis?.let { (it - grantedToday).coerceAtLeast(0L) },
            // A cap lockout is read from the lockout row, not inferred from a zero
            // allowance: the win that spends the cap grants access AND writes the
            // lockout, so both are true at once and only the row separates them.
            capLocked = repository.lockoutReason(pkg) == EnforcementRepository.LOCKOUT_REASON_DAILY_CAP &&
                lockoutRemaining > 0L,
            // At the ceiling the wait is whichever is longer: the lockout the last loss
            // wrote, or the debt standing behind it.
            debtLockRemainingMillis = if (AccessPolicy.isDebtAtCap(debt, cap)) {
                maxOf(lockoutRemaining, debt)
            } else {
                0L
            },
        )
    }

    fun deal() {
        val pkg = _state.value.targetPackage
        if (pkg.isEmpty()) return
        if (_state.value.capLocked) return // the cap is spent: a hand cannot grant access today
        // At the debt ceiling the wait is the way out, not another hand — the deep guard
        // behind the panel switch, so no path can start a game that should not run.
        if (_state.value.debtLockRemainingMillis > 0L) return
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
            lastHandGranted = false,
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
            settle(current.targetPackage, HandOutcome.LOSE)
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
        // "Play again" resets the table without leaving the blocker.
        _state.value = _state.value.copy(
            phase = TableState.Phase.IDLE,
            playerHand = null,
            dealerUp = null,
            dealerFull = null,
            showResult = false,
            lastOutcome = null,
            lastHandGranted = false,
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
            settle(pkg, outcome)
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
     */
    private fun settle(pkg: String, outcome: HandOutcome) {
        scope.launch {
            val grantedUntil = repository.onHandResult(pkg, outcome)
            if (grantedUntil != null) {
                expiryScheduler.scheduleExpiry(pkg, grantedUntil)
            }
            if (_state.value.targetPackage == pkg && _state.value.phase == TableState.Phase.RESOLVED) {
                // Mark the resolved state first so refreshPolicy's copy keeps it. The
                // grant result is recorded here because a WIN can still grant nothing
                // (spent cap), and the gate's success branch must not fire without a
                // grant behind it. refreshPolicy re-reads the debt as well.
                _state.value = _state.value.copy(
                    showResult = true,
                    lastOutcome = outcome,
                    lastHandGranted = grantedUntil != null,
                )
                refreshPolicy()
            }
        }
    }
}
