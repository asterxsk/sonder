package com.example.sonder.domain

import com.example.sonder.domain.model.Card
import com.example.sonder.domain.model.Hand
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.Rank
import com.example.sonder.domain.model.Suit
import kotlin.random.Random

/**
 * Pure blackjack rules (design_v3 §9, plan §1):
 * - single 52-card deck reshuffled each hand
 * - dealer stands on all 17s (including soft 17)
 * - natural blackjack is an instant win
 * - push is a free replay (no debt change)
 * Player actions are HIT and STAND only (no splits/doubles in v1 scope).
 */
object BlackjackRules {

    fun fullDeck(): List<Card> = Suit.entries.flatMap { suit -> Rank.entries.map { Card(it, suit) } }

    fun shuffledDeck(random: Random = Random.Default): List<Card> = fullDeck().shuffled(random)

    /** Initial deal: player, dealer(up), player, dealer(hole). */
    fun deal(deck: List<Card>): DealtHand {
        require(deck.size >= 4) { "need at least 4 cards to deal" }
        return DealtHand(
            player = Hand(listOf(deck[0], deck[2])),
            dealerUp = Hand(listOf(deck[1])),
            dealerHole = deck[3],
            remainingDeck = deck.drop(4),
        )
    }

    fun draw(deck: List<Card>): Pair<Card, List<Card>> {
        require(deck.isNotEmpty()) { "deck empty — reshuffle" }
        return deck.first() to deck.drop(1)
    }

    /**
     * Dealer reveals the hole card and draws until reaching 17+ (stands on all 17s,
     * including soft 17). Returns the final dealer hand and remaining deck.
     */
    fun dealerPlay(hand: Hand, hole: Card, deck: List<Card>): DealerPlayResult {
        var cards = hand.cards + hole
        var remaining = deck
        var current = Hand(cards)
        while (current.total < 17) {
            val (card, rest) = draw(remaining)
            cards = cards + card
            remaining = rest
            current = Hand(cards)
        }
        return DealerPlayResult(Hand(cards), remaining)
    }

    /** Outcome for a completed round, given both hands settled. */
    fun settle(player: Hand, dealer: Hand): HandOutcome = when {
        player.isBust -> HandOutcome.LOSE
        dealer.isBust -> HandOutcome.WIN
        else -> when (player.total.compareTo(dealer.total)) {
            0 -> HandOutcome.PUSH
            1 -> HandOutcome.WIN
            else -> HandOutcome.LOSE
        }
    }
}

data class DealtHand(
    val player: Hand,
    val dealerUp: Hand,
    val dealerHole: Card,
    val remainingDeck: List<Card>,
)

data class DealerPlayResult(
    val dealer: Hand,
    val remainingDeck: List<Card>,
)
