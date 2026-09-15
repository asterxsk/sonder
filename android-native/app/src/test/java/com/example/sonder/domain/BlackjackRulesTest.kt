package com.example.sonder.domain

import com.example.sonder.domain.model.Card
import com.example.sonder.domain.model.Hand
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.Rank
import com.example.sonder.domain.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every rule in plan §1 as a named test case. */
class BlackjackRulesTest {

    private fun card(rank: Rank, suit: Suit = Suit.SPADES) = Card(rank, suit)

    // ---- hand values ----

    @Test
    fun `hard hand totals correctly`() {
        assertEquals(16, Hand(listOf(card(Rank.NINE), card(Rank.SEVEN))).total)
    }

    @Test
    fun `soft ace counts as 11 when it fits`() {
        assertEquals(21, Hand(listOf(card(Rank.ACE), card(Rank.KING))).total)
        assertTrue(Hand(listOf(card(Rank.ACE), card(Rank.KING))).isSoft)
    }

    @Test
    fun `ace degrades to 1 to avoid bust`() {
        assertEquals(17, Hand(listOf(card(Rank.ACE), card(Rank.SIX), card(Rank.TEN))).total)
        assertFalse(Hand(listOf(card(Rank.ACE), card(Rank.SIX), card(Rank.TEN))).isSoft)
    }

    @Test
    fun `double ace hand counts one as 11`() {
        assertEquals(12, Hand(listOf(card(Rank.ACE), card(Rank.ACE))).total)
        assertTrue(Hand(listOf(card(Rank.ACE), card(Rank.ACE))).isSoft)
    }

    @Test
    fun `bust detection`() {
        assertTrue(Hand(listOf(card(Rank.TEN), card(Rank.TEN), card(Rank.TWO))).isBust)
    }

    @Test
    fun `natural blackjack detection`() {
        assertTrue(Hand(listOf(card(Rank.ACE), card(Rank.JACK))).isNatural)
        assertFalse(Hand(listOf(card(Rank.TEN), card(Rank.TEN), card(Rank.ACE))).isNatural) // 21 but 3 cards
    }

    // ---- dealing ----

    @Test
    fun `deal distributes four cards in order`() {
        val deck = BlackjackRules.fullDeck()
        val dealt = BlackjackRules.deal(deck)
        assertEquals(2, dealt.player.cards.size)
        assertEquals(1, dealt.dealerUp.cards.size)
        assertEquals(deck[0], dealt.player.cards[0])
        assertEquals(deck[1], dealt.dealerUp.cards[0])
        assertEquals(deck[3], dealt.dealerHole)
        assertEquals(48, dealt.remainingDeck.size)
    }

    @Test
    fun `deck has 52 unique cards`() {
        val deck = BlackjackRules.fullDeck()
        assertEquals(52, deck.size)
        assertEquals(52, deck.toSet().size)
    }

    // ---- dealer play ----

    @Test
    fun `dealer stands on hard 17`() {
        // Up cards 10+2, hole 5 → reveals to 17 and stops.
        val hand = Hand(listOf(card(Rank.TEN), card(Rank.TWO)))
        val result = BlackjackRules.dealerPlay(hand, card(Rank.FIVE), BlackjackRules.fullDeck())
        assertEquals(17, result.dealer.total)
        assertEquals(3, result.dealer.cards.size)
    }

    @Test
    fun `dealer stands on soft 17`() {
        // A + 6 up, hole 10: total degrades the ace to 27→17; dealer must stand, no 4th card.
        val hand = Hand(listOf(card(Rank.ACE), card(Rank.SIX)))
        val result = BlackjackRules.dealerPlay(hand, card(Rank.TEN), BlackjackRules.fullDeck())
        assertEquals(17, result.dealer.total)
        assertEquals(3, result.dealer.cards.size)
    }

    @Test
    fun `dealer draws until 17 from low total`() {
        val hand = Hand(listOf(card(Rank.TWO), card(Rank.THREE)))
        val result = BlackjackRules.dealerPlay(hand, card(Rank.FOUR), BlackjackRules.fullDeck())
        assertTrue(result.dealer.total >= 17)
    }

    // ---- settlement ----

    @Test
    fun `player bust always loses`() {
        val player = Hand(listOf(card(Rank.TEN), card(Rank.TEN), card(Rank.TWO)))
        val dealer = Hand(listOf(card(Rank.TEN), card(Rank.TEN), card(Rank.TWO)))
        assertEquals(HandOutcome.LOSE, BlackjackRules.settle(player, dealer))
    }

    @Test
    fun `dealer bust with live player wins`() {
        val player = Hand(listOf(card(Rank.TEN), card(Rank.NINE)))
        val dealer = Hand(listOf(card(Rank.TEN), card(Rank.TEN), card(Rank.TWO)))
        assertEquals(HandOutcome.WIN, BlackjackRules.settle(player, dealer))
    }

    @Test
    fun `higher total wins`() {
        assertEquals(
            HandOutcome.WIN,
            BlackjackRules.settle(
                Hand(listOf(card(Rank.TEN), card(Rank.NINE))),
                Hand(listOf(card(Rank.TEN), card(Rank.EIGHT))),
            ),
        )
        assertEquals(
            HandOutcome.LOSE,
            BlackjackRules.settle(
                Hand(listOf(card(Rank.TEN), card(Rank.EIGHT))),
                Hand(listOf(card(Rank.TEN), card(Rank.NINE))),
            ),
        )
    }

    @Test
    fun `equal total pushes`() {
        assertEquals(
            HandOutcome.PUSH,
            BlackjackRules.settle(
                Hand(listOf(card(Rank.TEN), card(Rank.NINE))),
                Hand(listOf(card(Rank.NINE), card(Rank.TEN))),
            ),
        )
    }
}
