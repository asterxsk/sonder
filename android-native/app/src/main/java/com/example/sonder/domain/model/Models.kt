package com.example.sonder.domain.model

/** Pure domain models — no Android imports. design_v3.md §20 keeps these testable. */

enum class Suit { SPADES, HEARTS, DIAMONDS, CLUBS }

enum class Rank(val display: String, val baseValue: Int) {
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5),
    SIX("6", 6), SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9),
    TEN("10", 10), JACK("J", 10), QUEEN("Q", 10), KING("K", 10),
    ACE("A", 11),
}

data class Card(val rank: Rank, val suit: Suit) {
    /** e.g. "A♠" */
    val label: String get() = "${rank.display}${suitGlyph(suit)}"

    private fun suitGlyph(s: Suit): String = when (s) {
        Suit.SPADES -> "♠"
        Suit.HEARTS -> "♥"
        Suit.DIAMONDS -> "♦"
        Suit.CLUBS -> "♣"
    }
}

data class Hand(val cards: List<Card>) {
    /** Best total: aces count 11 unless it would bust, then they count 1. */
    val total: Int
        get() {
            val base = cards.sumOf { it.rank.baseValue }
            val aces = cards.count { it.rank == Rank.ACE }
            var total = base
            var softAces = aces
            while (total > 21 && softAces > 0) {
                total -= 10
                softAces--
            }
            return total
        }

    /** True if at least one ace is still counted as 11 in the best total. */
    val isSoft: Boolean
        get() {
            val base = cards.sumOf { it.rank.baseValue }
            val aces = cards.count { it.rank == Rank.ACE }
            return aces > 0 && base - total < 10 * aces
        }

    val isBust: Boolean get() = total > 21
    val isNatural: Boolean get() = cards.size == 2 && total == 21
}

enum class HandOutcome { WIN, LOSE, PUSH }
