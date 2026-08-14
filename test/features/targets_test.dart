import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/domain/models.dart';
import 'package:sonder/features/app/app_state.dart';
import 'package:sonder/features/targets/targets_screen.dart';

import 'fakes/fake_platform.dart';

void main() {
  testWidgets('Targets shows defaults and best-effort disclosure', (
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
        home: TargetsScreen(appState: state, platform: platform),
      ),
    );
    await tester.pump();

    expect(find.text('APP CONFIGURATION'), findsOneWidget);
    expect(find.text('YouTube'), findsOneWidget);
    expect(find.text('Instagram'), findsOneWidget);
    expect(find.text('BEST-EFFORT DETECTION'), findsOneWidget);
    expect(find.textContaining('best-effort'), findsOneWidget);
    expect(find.text('ADD APP TARGET'), findsOneWidget);
    expect(find.byIcon(Icons.close), findsNothing);
  });

  testWidgets('Manual app target can be added via state seam', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform()
      ..launchableAppsToReturn = const [
        LaunchableApp(
          packageName: 'com.example.game',
          displayName: 'Example Game',
        ),
      ];
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();

    state.addCustomTarget(
      const LaunchableApp(
        packageName: 'com.example.game',
        displayName: 'Example Game',
      ),
    );
    expect(
      state.targets.any((t) => t.packageName == 'com.example.game'),
      isTrue,
    );
    final added = state.targetFor('com.example.game')!;
    expect(added.surface, TargetSurface.wholeApp);

    await tester.pumpWidget(
      MaterialApp(
        home: TargetsScreen(appState: state, platform: platform),
      ),
    );
    await tester.pump();
    expect(find.text('Example Game'), findsOneWidget);
    expect(find.byIcon(Icons.close), findsNothing);
  });

  testWidgets('State can remove target without an in-page remove action', (
    WidgetTester tester,
  ) async {
    final FakeAccessPlatform platform = FakeAccessPlatform();
    final AppState state = AppState(
      platform: platform,
      nowEpochMs: () => 0,
      enableTick: false,
    );
    await state.init();
    state.addCustomTarget(
      const LaunchableApp(
        packageName: 'com.example.game',
        displayName: 'Example Game',
      ),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: TargetsScreen(appState: state, platform: platform),
      ),
    );
    await tester.pump();
    expect(find.text('Example Game'), findsOneWidget);

    state.removeTarget('com.example.game');
    await tester.pump();
    expect(find.text('Example Game'), findsNothing);
  });

  testWidgets('Targets wholeApp disclosure text present', (
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
        home: TargetsScreen(appState: state, platform: platform),
      ),
    );
    await tester.pump();
    expect(find.textContaining('package-level'), findsOneWidget);
  });
}
