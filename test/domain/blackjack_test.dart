import 'dart:math';

import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/domain/blackjack.dart';

Card c(Rank r, [Suit s = Suit.hearts]) => Card(s, r);

/// Builds an ordered deck where the first elements are dealt first.
///
/// Deal order: player0, player1, dealer0, dealer1, then remaining hits in order.
List<Card> deck(List<Card> inDealOrder, {List<Card>? remainder}) {
  final d = List<Card>.from(inDealOrder);
  if (remainder != null) d.addAll(remainder);
  // Pad to at least 52 if needed for engine that uses remaining deck.
  while (d.length < 12) {
    d.add(const Card(Suit.clubs, Rank.two));
  }
  return d;
}

void main() {
  group('handValue / ace handling', () {
    test('ace soft 11', () {
      expect(handValue([c(Rank.ace), c(Rank.six)]), 17);
      expect(isSoft([c(Rank.ace), c(Rank.six)]), isTrue);
    });

    test('ace hard when would bust', () {
      // A + 9 + 5 = 15 (A as 1)
      expect(handValue([c(Rank.ace), c(Rank.nine), c(Rank.five)]), 15);
      expect(isSoft([c(Rank.ace), c(Rank.nine), c(Rank.five)]), isFalse);
    });

    test('two aces: 12 (11+1)', () {
      expect(handValue([c(Rank.ace), c(Rank.ace)]), 12);
      expect(isSoft([c(Rank.ace), c(Rank.ace)]), isTrue);
    });

    test('three aces + 8 = 21 (A+A+A+8: 11+1+1+8)', () {
      expect(
        handValue([c(Rank.ace), c(Rank.ace), c(Rank.ace), c(Rank.eight)]),
        21,
      );
    });

    test('ace + king = 21 blackjack value', () {
      expect(handValue([c(Rank.ace), c(Rank.king)]), 21);
      expect(isBlackjack([c(Rank.ace), c(Rank.king)]), isTrue);
    });

    test('10+10+5 = 25 bust', () {
      expect(isBust([c(Rank.ten), c(Rank.ten), c(Rank.five)]), isTrue);
    });

    test('face cards are 10', () {
      expect(handValue([c(Rank.jack)]), 10);
      expect(handValue([c(Rank.queen)]), 10);
      expect(handValue([c(Rank.king)]), 10);
    });

    test('21 with 3 cards is not blackjack', () {
      expect(
        isBlackjack([c(Rank.seven), c(Rank.seven), c(Rank.seven)]),
        isFalse,
      );
      expect(handValue([c(Rank.seven), c(Rank.seven), c(Rank.seven)]), 21);
    });
  });

  group('BlackjackEngine - natural blackjack', () {
    test('player natural vs dealer non-natural => playerBlackjack', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      final d = deck([c(Rank.ace), c(Rank.king), c(Rank.ten), c(Rank.nine)]);
      final h = eng.dealHandFromDeck(d);
      expect(h.isComplete, isTrue);
      expect(h.outcome, BlackjackOutcome.playerBlackjack);
    });

    test('dealer natural vs player non-natural => dealerBlackjack', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      final d = deck([c(Rank.ten), c(Rank.nine), c(Rank.ace), c(Rank.king)]);
      final h = eng.dealHandFromDeck(d);
      expect(h.outcome, BlackjackOutcome.dealerBlackjack);
    });

    test('both natural => push', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      final d = deck([c(Rank.ace), c(Rank.king), c(Rank.ace), c(Rank.queen)]);
      final h = eng.dealHandFromDeck(d);
      expect(h.outcome, BlackjackOutcome.push);
    });

    test('no natural => not complete', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      final d = deck([c(Rank.ten), c(Rank.nine), c(Rank.ten), c(Rank.eight)]);
      final h = eng.dealHandFromDeck(d);
      expect(h.isComplete, isFalse);
      expect(h.outcome, isNull);
    });
  });

  group('player hit / bust', () {
    test('player bust after hit => dealerWin', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      // Player 10+6=16, Dealer 10+6=16, next card K (10) => player 26 bust.
      final d = deck([
        c(Rank.ten),
        c(Rank.six),
        c(Rank.ten),
        c(Rank.six),
        c(Rank.king),
      ]);
      var h = eng.dealHandFromDeck(d);
      expect(h.isComplete, isFalse);
      h = eng.play(h, PlayerAction.hit);
      expect(h.isComplete, isTrue);
      expect(h.outcome, BlackjackOutcome.dealerWin);
      expect(h.playerBust, isTrue);
    });

    test('player hit to 21 then stand wins if dealer lower', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      // Player 10+5=15, Dealer 10+6=16, hit 6 => 21, dealer draws etc.
      final d = deck([
        c(Rank.ten),
        c(Rank.five),
        c(Rank.ten),
        c(Rank.six),
        c(Rank.six), // player hit
        c(Rank.two), // dealer draw if needed
      ]);
      var h = eng.dealHandFromDeck(d);
      h = eng.play(h, PlayerAction.hit);
      expect(h.playerValue, 21);
      expect(h.isComplete, isFalse);
      h = eng.play(h, PlayerAction.stand);
      expect(h.isComplete, isTrue);
      // Player 21 vs dealer 16 -> dealer draws 2 => 18 => player wins.
      expect(h.outcome, BlackjackOutcome.playerWin);
    });
  });

  group('dealer policy stands on all 17', () {
    test('dealer stands on hard 17', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      // Player 10+7=17, Dealer 10+7=17, player stands.
      final d = deck([c(Rank.ten), c(Rank.seven), c(Rank.ten), c(Rank.seven)]);
      var h = eng.dealHandFromDeck(d);
      h = eng.play(h, PlayerAction.stand);
      expect(h.dealerValue, 17);
      expect(h.outcome, BlackjackOutcome.push);
    });

    test('dealer stands on soft 17 (A+6)', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      // Player 10+7=17, Dealer A+6=17 soft, next card would be 5 but dealer must stand.
      final d = deck([
        c(Rank.ten),
        c(Rank.seven),
        c(Rank.ace),
        c(Rank.six),
        c(Rank.five),
      ]);
      var h = eng.dealHandFromDeck(d);
      h = eng.play(h, PlayerAction.stand);
      expect(h.dealerValue, 17);
      expect(isSoft(h.dealerCards), isTrue);
      // Push at 17 vs 17; dealer did not draw the 5.
      expect(h.dealerCards.length, 2);
      expect(h.outcome, BlackjackOutcome.push);
    });

    test('dealer hits below 17 and busts => playerWin', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      // Player 10+8=18, Dealer 10+6=16 -> hits 10 => 26 bust.
      final d = deck([
        c(Rank.ten),
        c(Rank.eight),
        c(Rank.ten),
        c(Rank.six),
        c(Rank.ten),
      ]);
      var h = eng.dealHandFromDeck(d);
      h = eng.play(h, PlayerAction.stand);
      expect(h.dealerBust, isTrue);
      expect(h.outcome, BlackjackOutcome.playerWin);
    });

    test('dealer hits to 17+ and wins', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      // Player 10+6=16, Dealer 10+4=14 -> hits 7 => 21 => dealerWin.
      final d = deck([
        c(Rank.ten),
        c(Rank.six),
        c(Rank.ten),
        c(Rank.four),
        c(Rank.seven),
      ]);
      var h = eng.dealHandFromDeck(d);
      h = eng.play(h, PlayerAction.stand);
      expect(h.dealerValue, 21);
      expect(h.outcome, BlackjackOutcome.dealerWin);
    });
  });

  group('push handling', () {
    test('push outcome is distinct and isWinForAccess returns null', () {
      expect(isWinForAccess(BlackjackOutcome.push), isNull);
      expect(isWinForAccess(BlackjackOutcome.playerWin), isTrue);
      expect(isWinForAccess(BlackjackOutcome.dealerWin), isFalse);
      expect(isWinForAccess(BlackjackOutcome.playerBlackjack), isTrue);
      expect(isWinForAccess(BlackjackOutcome.dealerBlackjack), isFalse);
    });

    test('equal totals after stand => push', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      final d = deck([c(Rank.ten), c(Rank.eight), c(Rank.ten), c(Rank.eight)]);
      var h = eng.dealHandFromDeck(d);
      h = eng.play(h, PlayerAction.stand);
      expect(h.outcome, BlackjackOutcome.push);
    });
  });

  group('deterministic / injectable deck', () {
    test('same ordered deck yields same outcome', () {
      final ordered = deck([
        c(Rank.ten),
        c(Rank.nine),
        c(Rank.ten),
        c(Rank.eight),
      ]);
      final e1 = BlackjackEngine(deckFactory: () => deck([]));
      final e2 = BlackjackEngine(deckFactory: () => deck([]));
      final h1 = e1.dealHandFromDeck(List.from(ordered));
      final h2 = e2.dealHandFromDeck(List.from(ordered));
      expect(h1.playerCards, h2.playerCards);
      expect(h1.dealerCards, h2.dealerCards);
    });

    test('Fisher-Yates with fixed seed is deterministic', () {
      List<Card> factory() => createDeck();
      final e1 = BlackjackEngine(random: Random(42), deckFactory: factory);
      final e2 = BlackjackEngine(random: Random(42), deckFactory: factory);
      final h1 = e1.dealHand();
      final h2 = e2.dealHand();
      expect(h1.playerCards, h2.playerCards);
      expect(h1.dealerCards, h2.dealerCards);
    });

    test('fresh 52-card deck per hand and 4 cards dealt', () {
      final eng = BlackjackEngine(random: Random(1));
      final h = eng.dealHand();
      expect(h.playerCards.length, 2);
      expect(h.dealerCards.length, 2);
      expect(h.remainingDeck.length, 48);
      final allDealt = [...h.playerCards, ...h.dealerCards, ...h.remainingDeck];
      expect(allDealt.toSet().length, 52);
    });

    test('play on completed hand returns same instance', () {
      final eng = BlackjackEngine(deckFactory: () => deck([]));
      final d = deck([c(Rank.ace), c(Rank.king), c(Rank.ten), c(Rank.nine)]);
      final h = eng.dealHandFromDeck(d);
      expect(h.isComplete, isTrue);
      final h2 = eng.play(h, PlayerAction.hit);
      expect(identical(h, h2), isTrue);
    });
  });
}
