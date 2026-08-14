import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/features/app/app_state.dart';
import 'package:sonder/features/blackjack/result_screen.dart';

import 'fakes/fake_platform.dart';

void main() {
  testWidgets('Result win shows 5 minutes and return path', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    int now = 0;
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => now,
      enableTick: false,
    );
    await state.init();
    await state.applyOutcome('com.google.android.youtube', won: true);

    await tester.pumpWidget(
      MaterialApp(
        home: ResultScreen(
          packageName: 'com.google.android.youtube',
          won: true,
          appState: state,
          onDone: () {},
        ),
      ),
    );
    await tester.pump();
    expect(find.text('5 MIN EARNED'), findsOneWidget);
    expect(find.textContaining('RETURN TO'), findsOneWidget);
  });

  testWidgets('Result loss shows 10-minute lockout', (
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
        home: ResultScreen(
          packageName: 'com.google.android.youtube',
          won: false,
          appState: state,
          onDone: () {},
        ),
      ),
    );
    await tester.pump();
    expect(find.text('LOCKED'), findsWidgets);
    expect(find.textContaining('10'), findsWidgets);
  });
}
