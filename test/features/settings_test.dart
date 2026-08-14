import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/features/app/app_state.dart';
import 'package:sonder/features/settings/settings_screen.dart';

import 'fakes/fake_platform.dart';

void main() {
  testWidgets('Settings shows permission states and disclosures', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();
    final ValueNotifier<ThemeMode> themeMode = ValueNotifier<ThemeMode>(
      ThemeMode.system,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsScreen(
          appState: state,
          platform: platform,
          themeMode: themeMode,
        ),
      ),
    );
    await tester.pump();

    expect(find.text('APP SETTINGS'), findsOneWidget);
    expect(find.text('PERMISSIONS'), findsOneWidget);
    expect(find.text('Accessibility Service'), findsOneWidget);
    expect(find.textContaining('Overlay'), findsOneWidget);
    expect(find.text('LIMITATIONS'), findsNothing);
    expect(find.text('ABOUT'), findsNothing);
    expect(find.textContaining('best-effort'), findsNothing);
    expect(find.textContaining('Play policy review'), findsNothing);
    expect(find.text('Theme'), findsOneWidget);
  });

  testWidgets('Settings open settings action calls platform', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();
    final ValueNotifier<ThemeMode> themeMode = ValueNotifier<ThemeMode>(
      ThemeMode.light,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsScreen(
          appState: state,
          platform: platform,
          themeMode: themeMode,
        ),
      ),
    );
    await tester.pump();

    await tester.tap(find.text('OPEN SETTINGS').first);
    await tester.pump();
    expect(platform.openSettingsCalled, isTrue);
  });

  testWidgets('Settings theme segmented control switches mode', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();
    final ValueNotifier<ThemeMode> themeMode = ValueNotifier<ThemeMode>(
      ThemeMode.system,
    );

    await tester.pumpWidget(
      MaterialApp(
        home: SettingsScreen(
          appState: state,
          platform: platform,
          themeMode: themeMode,
        ),
      ),
    );
    await tester.pump();
    expect(themeMode.value, ThemeMode.system);
    await tester.tap(find.text('LIGHT'));
    await tester.pump();
    expect(themeMode.value, ThemeMode.light);
  });
}
