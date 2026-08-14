import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/app.dart';
import 'package:sonder/design/tokens.dart';
import 'package:sonder/design/buttons.dart';
import 'package:sonder/design/panel.dart';
import 'package:sonder/design/status.dart';
import 'package:sonder/features/app/app_state.dart';
import 'package:sonder/features/onboarding/onboarding_shell.dart';

import '../features/fakes/fake_platform.dart';

void main() {
  group('Sonder tokens — exact visual contract', () {
    test('dark palette matches plan hex values', () {
      expect(SonderPalette.darkBg.value, 0xFF080B0F);
      expect(SonderPalette.darkSurface.value, 0xFF181A22);
      expect(SonderPalette.darkBorder.value, 0xFF202033);
      expect(SonderPalette.darkPrimary.value, 0xFF7E6CFF);
      expect(SonderPalette.darkPrimaryMuted.value, 0xFF9B8CFF);
      expect(SonderPalette.darkLoss.value, 0xFFFF609A);
      expect(SonderPalette.darkWin.value, 0xFF86EFA6);
      expect(SonderPalette.darkText.value, 0xFFE6E6E6);
    });

    test('light palette matches plan hex values', () {
      expect(SonderPalette.lightBg.value, 0xFFF8F8F2);
      expect(SonderPalette.lightSurface.value, 0xFFECEBE4);
      expect(SonderPalette.lightBorder.value, 0xFFD6D6C8);
      expect(SonderPalette.lightPrimary.value, 0xFF6B5CFF);
      expect(SonderPalette.lightPrimaryMuted.value, 0xFF9B8CFF);
      expect(SonderPalette.lightLoss.value, 0xFFFF609A);
      expect(SonderPalette.lightWin.value, 0xFF22C55E);
      expect(SonderPalette.lightText.value, 0xFF1A1A1A);
    });

    testWidgets('status labels carry explicit textual state, not color alone', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: buildSonderTheme(isDark: false),
          home: const Scaffold(
            body: Column(
              children: [
                SonderStateLabel(text: 'WIN  +5 MIN'),
                SonderStateLabel(text: 'LOSE  LOCKED 10 MIN'),
                SonderStateLabel(text: 'LOCKED'),
                SonderStatusChip(label: '5 MIN EARNED'),
              ],
            ),
          ),
        ),
      );
      expect(find.text('WIN  +5 MIN'), findsOneWidget);
      expect(find.text('LOSE  LOCKED 10 MIN'), findsOneWidget);
      expect(find.text('LOCKED'), findsOneWidget);
      expect(find.text('5 MIN EARNED'), findsOneWidget);
    });

    testWidgets('dark and light themes preserve semantic hierarchy', (
      WidgetTester tester,
    ) async {
      final ThemeData dark = buildSonderTheme(isDark: true);
      final ThemeData light = buildSonderTheme(isDark: false);
      // Same component roles, different surface/bg but same status tokens shape.
      expect(dark.colorScheme.tertiary, SonderPalette.darkWin);
      expect(light.colorScheme.tertiary, SonderPalette.lightWin);
      expect(dark.colorScheme.error, SonderPalette.darkLoss);
      expect(light.colorScheme.error, SonderPalette.lightLoss);
      expect(dark.scaffoldBackgroundColor, SonderPalette.darkBg);
      expect(light.scaffoldBackgroundColor, SonderPalette.lightBg);
    });

    testWidgets('design primitives respect accessible labels', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: buildSonderTheme(isDark: false),
          home: const Scaffold(
            body: Column(
              children: [
                SonderPanel(
                  semanticLabel: 'Time bank',
                  child: Text('panel-child'),
                ),
                SonderPrimaryButton(
                  label: 'PLAY PRIMARY',
                  semanticLabel: 'Play blackjack',
                ),
                SonderSecondaryButton(label: 'CANCEL'),
                SonderGhostButton(label: 'Skip'),
              ],
            ),
          ),
        ),
      );
      // Verify the panel carries its semantic label via widget property (TalkBack).
      final SonderPanel panel = tester.widget<SonderPanel>(
        find.byType(SonderPanel),
      );
      expect(panel.semanticLabel, 'Time bank');
      final SonderPrimaryButton primary = tester.widget<SonderPrimaryButton>(
        find.byType(SonderPrimaryButton),
      );
      expect(primary.semanticLabel, 'Play blackjack');
      expect(find.text('CANCEL'), findsOneWidget);
      expect(find.text('Skip'), findsOneWidget);
      // And the underlying Semantics widgets exist.
      expect(find.byType(Semantics), findsWidgets);
    });
  });

  group('Onboarding shell — rules and disclaimers', () {
    testWidgets('shows Sonder wordmark and WIN/LOCKED rules verbatim', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: buildSonderTheme(isDark: false),
          home: OnboardingShell(
            onFinished: () {},
            platform: FakeAccessPlatform(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      // Page 0 — welcome
      expect(find.text('SONDER'), findsOneWidget);
      expect(find.textContaining('be here, not everywhere.'), findsOneWidget);

      // Advance via NEXT button (more reliable than drag in tests)
      await tester.tap(find.text('NEXT'));
      await tester.pumpAndSettle();
      expect(find.textContaining('WIN  5 MIN'), findsOneWidget);
      expect(find.textContaining('LOSE  LOCKED 10 MIN'), findsOneWidget);
      expect(find.textContaining('20'), findsWidgets);

      // Advance to permissions
      await tester.tap(find.text('NEXT'));
      await tester.pumpAndSettle();
      expect(find.text('Accessibility Service'), findsOneWidget);
      // Permissions panel is scrollable — drag to reveal the limitations disclaimer.
      await tester.drag(find.byType(ListView).last, const Offset(0, -500));
      await tester.pumpAndSettle();
      expect(find.text('IMPORTANT LIMITATIONS'), findsOneWidget);
      expect(find.textContaining('best-effort'), findsOneWidget);
      expect(find.textContaining('bypass-proof'), findsOneWidget);
      expect(find.textContaining('Display over other apps'), findsOneWidget);
      expect(find.text('OPEN ACCESSIBILITY SETTINGS'), findsOneWidget);
    });

    testWidgets('permission button invokes callback seam', (
      WidgetTester tester,
    ) async {
      bool opened = false;
      await tester.pumpWidget(
        MaterialApp(
          home: OnboardingShell(
            onFinished: () {},
            onOpenAccessibilitySettings: () => opened = true,
            platform: FakeAccessPlatform(),
          ),
        ),
      );
      await tester.pumpAndSettle();
      // Go to permissions page via NEXT taps
      await tester.tap(find.text('NEXT'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('NEXT'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('OPEN ACCESSIBILITY SETTINGS'));
      await tester.pump();
      expect(opened, isTrue);
    });
  });

  group('SonderRoot bootstrap — system theme and onboarding entry', () {
    testWidgets('opens into onboarding and respects system theme by default', (
      WidgetTester tester,
    ) async {
      final FakeAccessPlatform platform = FakeAccessPlatform();
      final AppState appState = AppState(
        platform: platform,
        nowEpochMs: () => 0,
      );
      await appState.init();
      final themeMode = ValueNotifier<ThemeMode>(ThemeMode.system);

      await tester.pumpWidget(
        SonderRoot(
          appState: appState,
          platform: platform,
          themeMode: themeMode,
        ),
      );
      await tester.pump();
      expect(find.text('SONDER'), findsOneWidget);
      expect(themeMode.value, ThemeMode.system);
      // Dispose before verifyInvariants to cancel the 1s tick timer.
      appState.dispose();
      await tester.pump();
    });
  });

  group('Android launcher resources presence', () {
    test(
      'adaptive, round, monochrome variants and manifest reference Sonder',
      () {
        final bg = File(
          'android/app/src/main/res/drawable/ic_launcher_background.xml',
        );
        final fg = File(
          'android/app/src/main/res/drawable/ic_launcher_foreground.xml',
        );
        final mono = File(
          'android/app/src/main/res/drawable/ic_launcher_monochrome.xml',
        );
        final adaptive = File(
          'android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml',
        );
        final round = File(
          'android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml',
        );
        final master = File('assets/icon/sonder_icon_master.svg');
        final manifest = File(
          'android/app/src/main/AndroidManifest.xml',
        ).readAsStringSync();

        for (final f in [bg, fg, mono, adaptive, round, master]) {
          expect(f.existsSync(), isTrue, reason: 'Missing ${f.path}');
          expect(
            f.readAsStringSync().trim().isNotEmpty,
            isTrue,
            reason: 'Empty ${f.path}',
          );
        }
        expect(
          manifest,
          contains('android:label="Sonder"'),
          reason: 'App label must be exactly Sonder',
        );
        expect(
          manifest,
          contains('@mipmap/ic_launcher"'),
          reason: 'Manifest must reference adaptive launcher',
        );
        // Monochrome drawable must be referenced from adaptive icon
        expect(adaptive.readAsStringSync(), contains('monochrome'));
        expect(round.readAsStringSync(), contains('monochrome'));
        // Master SVG is near-black bg with heart+keyhole, no text
        final svg = master.readAsStringSync();
        expect(svg, contains('#080B0F'));
        expect(svg, contains('#7E6CFF'));
        expect(svg.toLowerCase(), isNot(contains('youtube')));
        expect(svg.toLowerCase(), isNot(contains('instagram')));
      },
    );
  });
}
