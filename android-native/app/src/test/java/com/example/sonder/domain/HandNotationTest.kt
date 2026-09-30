package com.example.sonder.domain

import com.example.sonder.domain.model.Card
import com.example.sonder.domain.model.Hand
import com.example.sonder.domain.model.Rank
import com.example.sonder.domain.model.Suit
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one line of history a played hand leaves behind. */
class HandNotationTest {

    private fun hand(vararg cards: Card) = Hand(cards.toList())

    private val aceSpades = Card(Rank.ACE, Suit.SPADES)
    private val kingHearts = Card(Rank.KING, Suit.HEARTS)
    private val nineDiamonds = Card(Rank.NINE, Suit.DIAMONDS)
    private val tenClubs = Card(Rank.TEN, Suit.CLUBS)

    @Test
    fun `cards are listed with their suits and the total`() {
        assertEquals("A♠ K♥ · 21", handNotation(hand(aceSpades, kingHearts)))
    }

    @Test
    fun `the total is the one that settled the hand`() {
        // The soft ace counts as 1 here, not 11 — the number on the ledger row has to be
        // the number the hand was lost by.
        assertEquals("A♠ 9♦ 10♣ · 20", handNotation(hand(aceSpades, nineDiamonds, tenClubs)))
    }

    @Test
    fun `a bust reads over twenty-one`() {
        assertEquals("10♣ 9♦ 5♥ · 24", handNotation(hand(tenClubs, nineDiamonds, Card(Rank.FIVE, Suit.HEARTS))))
    }

    @Test
    fun `a null hand has nothing to say`() {
        // A bust never reveals the dealer's hole card, so the dealer's side of that hand is
        // genuinely absent — the row omits the line rather than printing an empty one.
        assertEquals("", handNotation(null))
    }

    @Test
    fun `a hand with no cards is not a hand`() {
        assertEquals("", handNotation(Hand(emptyList())))
    }
}
