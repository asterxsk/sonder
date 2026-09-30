package com.example.sonder.domain

import com.example.sonder.domain.model.Hand

/**
 * A hand as one line of history — `"A♠ K♥ · 21"` — or empty for a hand there is nothing to
 * say about.
 *
 * The total travels with the cards on purpose. `A♠ K♥` is a table mid-hand; `A♠ K♥ · 21` is
 * the result, and the history is read weeks later by someone who wants to know what they won
 * with, not to re-add up the pips. [Hand.total] already applies the ace rule, so the number
 * printed is the number that settled the hand.
 *
 * Empty rather than a placeholder for a null or cardless hand: the ledger row omits the line
 * entirely, which is the truthful rendering of a hand played before cards were recorded.
 */
fun handNotation(hand: Hand?): String {
    if (hand == null || hand.cards.isEmpty()) return ""
    return "${hand.cards.joinToString(" ") { it.label }} · ${hand.total}"
}
