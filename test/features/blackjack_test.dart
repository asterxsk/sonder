import 'dart:math' show Random;
import 'package:flutter/material.dart' hide Card;
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/domain/blackjack.dart';
import 'package:sonder/features/app/app_state.dart';
import 'package:sonder/features/blackjack/blackjack_screen.dart';

import 'fakes/fake_platform.dart';

class SeededRandom implements Random {
  SeededRandom(int seed) : _inner = Random(seed);
  final Random _inner;
  @override
  bool nextBool() => _inner.nextBool();
  @override
  double nextDouble() => _inner.nextDouble();
  @override
  int nextInt(int max) => _inner.nextInt(max);
}

void main() {
  group('blackjack helpers via canonical engine', () {
    test('natural blackjack wins immediately', () {
      // Deal from a fixed ordered deck: player A+K (blackjack), dealer 5+7.
      // Use the ordered-deck entrypoint to avoid the shuffle overriding order.
      final List<Card> ordered = [
        const Card(Suit.spades, Rank.ace),
        const Card(Suit.spades, Rank.king),
        const Card(Suit.clubs, Rank.five),
        const Card(Suit.clubs, Rank.seven),
        ...createDeck(),
      ];
      final engine = BlackjackEngine(random: Random(0));
      final hand = engine.dealHandFromDeck(ordered);
      expect(hand.isComplete, isTrue);
      expect(
        hand.outcome,
        anyOf(BlackjackOutcome.playerBlackjack, BlackjackOutcome.playerWin),
      );
    });

    test('dealer stands on all 17', () {
      // Deal deterministically via ordered deck: player 10+7, dealer 10+7 (both 17).
      final List<Card> ordered = [
        const Card(Suit.spades, Rank.ten),
        const Card(Suit.hearts, Rank.seven),
        const Card(Suit.clubs, Rank.ten),
        const Card(Suit.diamonds, Rank.seven),
        const Card(Suit.spades, Rank.two),
        ...createDeck(),
      ];
      final engine = BlackjackEngine(random: Random(0));
      final dealt = engine.dealHandFromDeck(ordered);
      // Verify preconditions: both hands are hard 17
      expect(handValue(dealt.playerCards), 17);
      expect(handValue(dealt.dealerCards), 17);
      final stood = engine.play(dealt, PlayerAction.stand);
      expect(stood.dealerCards.length, 2);
      expect(handValue(stood.dealerCards), 17);
      expect(stood.isComplete, isTrue);
    });

    test('bust is detected', () {
      expect(
        isBust([
          const Card(Suit.spades, Rank.ten),
          const Card(Suit.hearts, Rank.ten),
          const Card(Suit.clubs, Rank.five),
        ]),
        isTrue,
      );
      expect(
        isBust([
          const Card(Suit.spades, Rank.ten),
          const Card(Suit.hearts, Rank.seven),
        ]),
        isFalse,
      );
    });
  });

  testWidgets('Blackjack screen renders totals and Hit/Stand', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();

    await tester.pumpWidget(
      MaterialApp(
        home: BlackjackScreen(
          packageName: 'com.google.android.youtube',
          appState: state,
          rngOverride: SeededRandom(1),
          onFinished: (_) {},
        ),
      ),
    );
    await tester.pump();
    // Rule disclosure always visible
    expect(find.textContaining('Dealer stands on all 17'), findsOneWidget);
    // Totals are labeled numerically
    expect(find.textContaining('YOU'), findsOneWidget);
    expect(find.textContaining('DEALER'), findsOneWidget);
    expect(find.text('HIT'), findsOneWidget);
    expect(find.text('STAND'), findsOneWidget);
    // No gambling vocabulary on interactive controls (bet/wager as action labels).
    // The disclaimer "No wagers. Just one hand." is allowed copy clarifying no wagers.
    expect(find.widgetWithText(ElevatedButton, 'bet'), findsNothing);
    expect(find.widgetWithText(OutlinedButton, 'wager'), findsNothing);
    expect(find.text('BET'), findsNothing);
    expect(find.text('WAGER'), findsNothing);
  });

  testWidgets('Blackjack screen STAND shows result panel with MIN', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();

    await tester.pumpWidget(
      MaterialApp(
        home: BlackjackScreen(
          packageName: 'com.google.android.youtube',
          appState: state,
          rngOverride: SeededRandom(1),
          onFinished: (_) {},
        ),
      ),
    );
    await tester.pump();
    await tester.tap(find.text('STAND'));
    await tester.pump();
    // After stand, a result panel is shown with either win/loss/push copy
    expect(find.textContaining('MIN'), findsWidgets);
  });

  testWidgets('Blackjack screen respects reduced motion (no crash)', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();

    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(disableAnimations: true),
        child: MaterialApp(
          home: BlackjackScreen(
            packageName: 'com.google.android.youtube',
            appState: state,
            rngOverride: SeededRandom(1),
            onFinished: (_) {},
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.text('HIT'), findsOneWidget);
    expect(find.text('STAND'), findsOneWidget);
  });
}
