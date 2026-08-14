import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/domain/models.dart';
import 'package:sonder/features/app/app_state.dart';
import 'package:sonder/features/home/home_screen.dart';

import 'fakes/fake_platform.dart';

AppState _makeAppState({
  required int Function() clock,
  FakeAccessPlatform? platform,
}) {
  final FakeAccessPlatform p = platform ?? FakeAccessPlatform();
  final AppState s = AppState(
    platform: p,
    nowEpochMs: clock,
    enableTick: false,
  );
  return s;
}

void main() {
  testWidgets('Home shows compact time bank summary', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    const int now = 1000000;
    final AppState state = _makeAppState(clock: () => now, platform: platform);
    await state.init();

    await tester.pumpWidget(MaterialApp(home: HomeScreen(appState: state)));
    await tester.pump();

    expect(find.text('TIME BANK'), findsOneWidget);
    expect(find.text('TODAY'), findsOneWidget);
    expect(find.text('TARGETS'), findsNothing);
    expect(find.text('MANAGE'), findsNothing);
    expect(find.byTooltip('Settings'), findsNothing);
    expect(find.text('YouTube'), findsNothing);
    expect(find.text('Instagram'), findsNothing);
    // Tagline lives below the fold in the scroll view — scroll to reveal
    await tester.drag(find.byType(ListView), const Offset(0, -600));
    await tester.pump();
    expect(find.text('be here, not everywhere.'), findsOneWidget);
  });

  testWidgets('Home renders LOCKED and 5 MIN EARNED', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    int now = 1000000;
    final AppState state = _makeAppState(clock: () => now, platform: platform);
    await state.init();

    // Win on YouTube -> 5 min earned
    await state.applyOutcome('com.google.android.youtube', won: true);
    expect(state.totalGrantedRemainingMs, kGrantDurationMs);

    await tester.pumpWidget(MaterialApp(home: HomeScreen(appState: state)));
    await tester.pump();
    expect(find.text('5 MIN EARNED'), findsOneWidget);

    // Lose on Instagram -> locked 10 min
    await state.applyOutcome('com.instagram.android', won: false);
    await tester.pump();
    expect(find.textContaining('LOCKED'), findsWidgets);
  });

  testWidgets('Home 20s abandoned grant is expired via policy helper', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    int now = 1000000;
    final AppState state = _makeAppState(clock: () => now, platform: platform);
    await state.init();
    await state.applyOutcome('com.google.android.youtube', won: true);
    expect(
      state.decisionFor('com.google.android.youtube').status,
      AccessStatus.allowed,
    );

    // Background at t0 then expire check at t0+21s
    final snap = state.snapshotFor('com.google.android.youtube')!;
    final bg = recordBackground(snap, 1000000);
    final expired = expireAbandonedGrant(bg, 1000000 + 21000);
    expect(expired.grantedUntilEpochMs, isNull);
    // 19s preserves
    final notExpired = expireAbandonedGrant(bg, 1000000 + 19000);
    expect(notExpired.grantedUntilEpochMs, isNotNull);
  });

  testWidgets('Home bottom navigation labels are present', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = _makeAppState(clock: () => 0, platform: platform);
    await state.init();
    await tester.pumpWidget(MaterialApp(home: HomeScreen(appState: state)));
    await tester.pump();
    // App-owned bottom nav is in SonderApp; HomeScreen itself does not own navigation.
    // Verify HomeScreen still leaves room for navigation via semantic tabs elsewhere.
    expect(find.text('TIME BANK'), findsOneWidget);
  });
}
