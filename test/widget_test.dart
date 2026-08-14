import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/app.dart';
import 'package:sonder/features/app/app_state.dart';

import 'features/fakes/fake_platform.dart';

void main() {
  testWidgets('SonderRoot boots into onboarding', (WidgetTester tester) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState appState = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await appState.init();
    final ValueNotifier<ThemeMode> themeMode = ValueNotifier<ThemeMode>(
      ThemeMode.light,
    );

    await tester.pumpWidget(
      SonderRoot(appState: appState, platform: platform, themeMode: themeMode),
    );
    await tester.pump();
    expect(find.text('SONDER'), findsOneWidget);
    appState.dispose();
    themeMode.dispose();
    platform.dispose();
    await tester.pump();
  });

  testWidgets('DashboardShell exposes four product pages', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState appState = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await appState.init();
    final ValueNotifier<ThemeMode> themeMode = ValueNotifier<ThemeMode>(
      ThemeMode.light,
    );
    addTearDown(() {
      appState.dispose();
      themeMode.dispose();
      platform.dispose();
    });

    await tester.pumpWidget(
      MaterialApp(
        home: DashboardShell(
          appState: appState,
          platform: platform,
          themeMode: themeMode,
        ),
      ),
    );
    expect(find.text('HOME'), findsOneWidget);

    await tester.tap(find.text('STATS'));
    await tester.pump();
    expect(find.text('WIN RATE'), findsOneWidget);

    await tester.tap(find.text('APPS'));
    await tester.pump();
    expect(find.text('APP CONFIGURATION'), findsOneWidget);

    await tester.tap(find.text('SETTINGS'));
    await tester.pump();
    expect(find.text('APP SETTINGS'), findsOneWidget);
  });
}
