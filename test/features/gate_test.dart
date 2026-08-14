import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/features/app/app_state.dart';
import 'package:sonder/features/gate/gate_screen.dart';

import 'fakes/fake_platform.dart';

void main() {
  testWidgets('Gate shows target, rules, and PLAY BLACKJACK when not locked', (
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
        home: GateScreen(
          packageName: 'com.google.android.youtube',
          appState: state,
          onPlay: () {},
        ),
      ),
    );
    await tester.pump();

    // kDefaultTargets uses displayName YouTube
    expect(find.text('YouTube'), findsOneWidget);
    expect(find.textContaining('One hand'), findsOneWidget);
    expect(find.text('PLAY BLACKJACK'), findsOneWidget);
    expect(find.textContaining('5 minutes'), findsWidgets);
    expect(find.textContaining('10 minutes'), findsWidgets);
    // Semantics on primary action
    expect(find.bySemanticsLabel(RegExp(r'Play blackjack')), findsOneWidget);
  });

  testWidgets('Gate shows LOCKED timer when locked', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();
    await state.applyOutcome('com.google.android.youtube', won: false);
    await tester.pumpWidget(
      MaterialApp(
        home: GateScreen(
          packageName: 'com.google.android.youtube',
          appState: state,
          onPlay: () {},
        ),
      ),
    );
    await tester.pump();
    expect(find.textContaining('LOCKED'), findsWidgets);
    expect(find.text('PLAY BLACKJACK'), findsNothing);
  });

  testWidgets('Gate exposes alternate target option', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();
    bool tapped = false;
    await tester.pumpWidget(
      MaterialApp(
        home: GateScreen(
          packageName: 'com.google.android.youtube',
          appState: state,
          onPlay: () {},
          onChooseAnotherApp: () => tapped = true,
        ),
      ),
    );
    await tester.pump();
    expect(find.text('CHOOSE ANOTHER APP'), findsOneWidget);
    await tester.tap(find.text('CHOOSE ANOTHER APP'));
    await tester.pump();
    expect(tapped, isTrue);
  });

  testWidgets('Gate shows best-effort note for Shorts surface', (
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
        home: GateScreen(
          packageName: 'com.google.android.youtube',
          appState: state,
          onPlay: () {},
        ),
      ),
    );
    await tester.pump();
    // Shorts/Reels disclosure is shown for surface targets
    expect(find.textContaining('best-effort'), findsOneWidget);
  });
}
