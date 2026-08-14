import 'dart:math';

/// Pure Dart blackjack implementation for Sonder's gate.
///
/// Rules: single 52-card deck per hand, Fisher-Yates shuffle with an
/// injectable [Random] (production default: [Random.secure()]), standard
/// values (A=1/11 soft, face=10), dealer stands on all 17, natural blackjack
/// evaluated before player actions, one hand grants the 5-minute or 10-minute
/// snapshot outcome.

enum Suit { hearts, diamonds, clubs, spades }

enum Rank {
  ace,
  two,
  three,
  four,
  five,
  six,
  seven,
  eight,
  nine,
  ten,
  jack,
  queen,
  king,
}

extension RankValue on Rank {
  int get pipValue {
    switch (this) {
      case Rank.ace:
        return 1;
      case Rank.two:
        return 2;
      case Rank.three:
        return 3;
      case Rank.four:
        return 4;
      case Rank.five:
        return 5;
      case Rank.six:
        return 6;
      case Rank.seven:
        return 7;
      case Rank.eight:
        return 8;
      case Rank.nine:
        return 9;
      case Rank.ten:
      case Rank.jack:
      case Rank.queen:
      case Rank.king:
        return 10;
    }
  }

  bool get isFace =>
      this == Rank.jack || this == Rank.queen || this == Rank.king;
}

class Card {
  final Suit suit;
  final Rank rank;

  const Card(this.suit, this.rank);

  @override
  bool operator ==(Object other) =>
      other is Card && other.suit == suit && other.rank == rank;

  @override
  int get hashCode => Object.hash(suit, rank);

  @override
  String toString() => '${rank.name} of ${suit.name}';
}

/// Best blackjack total for [cards] with ace soft/hard adjustment.
///
/// Counts aces as 11 where possible without busting; otherwise as 1.
int handValue(List<Card> cards) {
  var total = 0;
  var aces = 0;
  for (final c in cards) {
    if (c.rank == Rank.ace) {
      aces++;
      total += 11;
    } else {
      total += c.rank.pipValue;
    }
  }
  while (total > 21 && aces > 0) {
    total -= 10;
    aces--;
  }
  return total;
}

bool isBlackjack(List<Card> cards) =>
    cards.length == 2 && handValue(cards) == 21;

bool isBust(List<Card> cards) => handValue(cards) > 21;

/// Whether the hand is soft (contains an ace counted as 11).
bool isSoft(List<Card> cards) {
  var total = 0;
  var aces = 0;
  for (final c in cards) {
    if (c.rank == Rank.ace) {
      aces++;
      total += 1;
    } else {
      total += c.rank.pipValue;
    }
  }
  // If we can promote one ace from 1 to 11 without busting, it's soft.
  if (aces > 0 && total + 10 <= 21) return true;
  return false;
}

/// Creates a fresh 52-card deck.
List<Card> createDeck() {
  final deck = <Card>[];
  for (final suit in Suit.values) {
    for (final rank in Rank.values) {
      deck.add(Card(suit, rank));
    }
  }
  return deck;
}

/// Fisher-Yates shuffle. Returns a new list; input is not mutated.
List<Card> shuffledDeck(List<Card> deck, Random random) {
  final shuffled = List<Card>.from(deck);
  for (var i = shuffled.length - 1; i > 0; i--) {
    final j = random.nextInt(i + 1);
    final tmp = shuffled[i];
    shuffled[i] = shuffled[j];
    shuffled[j] = tmp;
  }
  return shuffled;
}

/// Outcome of a completed hand.
///
/// A [push] (tie) is not a win. For enforcement, consumers should treat
/// [push] the same as [dealerWin] (i.e. call `applyLoss` for the snapshot)
/// unless the UI explicitly offers a replay before any access decision is
/// persisted. The engine exposes [push] distinctly so the UI can choose
/// replay versus lock semantics.
enum BlackjackOutcome {
  playerWin,
  dealerWin,
  push,
  playerBlackjack,
  dealerBlackjack,
}

/// Player action during a hand.
enum PlayerAction { hit, stand }

/// State of an in-progress or completed hand.
class BlackjackHand {
  final List<Card> playerCards;
  final List<Card>
  dealerCards; // dealerCards[0] is face-up; full hand hidden until stand.
  final List<Card> remainingDeck;
  final bool isComplete;
  final BlackjackOutcome? outcome;

  const BlackjackHand({
    required this.playerCards,
    required this.dealerCards,
    required this.remainingDeck,
    required this.isComplete,
    this.outcome,
  });

  int get playerValue => handValue(playerCards);
  int get dealerValue => handValue(dealerCards);
  bool get playerBust => isBust(playerCards);
  bool get dealerBust => isBust(dealerCards);
}

/// Engine that deals and plays one hand at a time.
///
/// Injectable [Random] and deck factory allow deterministic tests.
class BlackjackEngine {
  final Random _random;
  final List<Card> Function()? deckFactory;

  BlackjackEngine({Random? random, this.deckFactory})
    : _random = random ?? Random.secure();

  List<Card> _newShuffledDeck() {
    final base = deckFactory != null ? deckFactory!() : createDeck();
    return shuffledDeck(base, _random);
  }

  /// Deals a new hand. Evaluates natural blackjacks immediately.
  BlackjackHand dealHand() {
    final deck = _newShuffledDeck();
    return dealHandFromDeck(deck);
  }

  /// Deals from a specific ordered deck (first elements dealt first).
  /// Useful for deterministic tests; deck must have at least 4 cards.
  BlackjackHand dealHandFromDeck(List<Card> orderedDeck) {
    assert(orderedDeck.length >= 4, 'Deck must have at least 4 cards');
    final deck = List<Card>.from(orderedDeck);
    final player = <Card>[deck.removeAt(0), deck.removeAt(0)];
    final dealer = <Card>[deck.removeAt(0), deck.removeAt(0)];

    final playerNatural = isBlackjack(player);
    final dealerNatural = isBlackjack(dealer);

    if (playerNatural || dealerNatural) {
      BlackjackOutcome outcome;
      if (playerNatural && dealerNatural) {
        outcome = BlackjackOutcome.push;
      } else if (playerNatural) {
        outcome = BlackjackOutcome.playerBlackjack;
      } else {
        outcome = BlackjackOutcome.dealerBlackjack;
      }
      return BlackjackHand(
        playerCards: player,
        dealerCards: dealer,
        remainingDeck: deck,
        isComplete: true,
        outcome: outcome,
      );
    }

    return BlackjackHand(
      playerCards: List.unmodifiable(player),
      dealerCards: List.unmodifiable(dealer),
      remainingDeck: deck,
      isComplete: false,
    );
  }

  /// Applies [action] to [hand]. Returns a new [BlackjackHand].
  ///
  /// If the hand is already complete, the same hand is returned.
  BlackjackHand play(BlackjackHand hand, PlayerAction action) {
    if (hand.isComplete) return hand;

    switch (action) {
      case PlayerAction.hit:
        return _hit(hand);
      case PlayerAction.stand:
        return _stand(hand);
    }
  }

  BlackjackHand _hit(BlackjackHand hand) {
    if (hand.remainingDeck.isEmpty) {
      // No cards left; treat as stand.
      return _stand(hand);
    }
    final deck = List<Card>.from(hand.remainingDeck);
    final player = List<Card>.from(hand.playerCards)..add(deck.removeAt(0));

    if (isBust(player)) {
      return BlackjackHand(
        playerCards: List.unmodifiable(player),
        dealerCards: hand.dealerCards,
        remainingDeck: deck,
        isComplete: true,
        outcome: BlackjackOutcome.dealerWin,
      );
    }

    return BlackjackHand(
      playerCards: List.unmodifiable(player),
      dealerCards: hand.dealerCards,
      remainingDeck: deck,
      isComplete: false,
    );
  }

  BlackjackHand _stand(BlackjackHand hand) {
    var deck = List<Card>.from(hand.remainingDeck);
    final dealer = List<Card>.from(hand.dealerCards);

    // Dealer draws to 17, stands on all 17 (including soft 17).
    while (handValue(dealer) < 17 && deck.isNotEmpty) {
      dealer.add(deck.removeAt(0));
    }

    BlackjackOutcome outcome;
    if (isBust(dealer)) {
      outcome = BlackjackOutcome.playerWin;
    } else {
      final pv = handValue(hand.playerCards);
      final dv = handValue(dealer);
      if (pv > dv) {
        outcome = BlackjackOutcome.playerWin;
      } else if (dv > pv) {
        outcome = BlackjackOutcome.dealerWin;
      } else {
        outcome = BlackjackOutcome.push;
      }
    }

    return BlackjackHand(
      playerCards: hand.playerCards,
      dealerCards: List.unmodifiable(dealer),
      remainingDeck: deck,
      isComplete: true,
      outcome: outcome,
    );
  }
}

/// Convenience: maps a blackjack outcome to whether the enforcement snapshot
/// should be a win (5-min grant) or loss (10-min lock).
///
/// Returns `true` for a grant, `false` for a lock, and `null` for a push
/// where the caller may want to offer a replay. If the caller instead wants
/// push-as-lock semantics, treat `null` as a loss.
bool? isWinForAccess(BlackjackOutcome outcome) {
  switch (outcome) {
    case BlackjackOutcome.playerWin:
    case BlackjackOutcome.playerBlackjack:
      return true;
    case BlackjackOutcome.dealerWin:
    case BlackjackOutcome.dealerBlackjack:
      return false;
    case BlackjackOutcome.push:
      return null;
  }
}
