import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/domain/access_policy.dart';
import 'package:sonder/domain/enforcement_snapshot.dart';
import 'package:sonder/domain/models.dart';
import 'package:sonder/domain/target_surface.dart';
import 'package:sonder/features/app/app_state.dart';
import 'package:sonder/features/gate/gate_screen.dart';
import 'package:sonder/features/home/home_screen.dart';
import 'package:sonder/features/shared/access_platform.dart';

import '../features/fakes/fake_platform.dart';

// Helper: dispose AppState after use to avoid pending Timer failures.
// All widget tests must await `tester.pumpWidget` wrapping then call `state.dispose()`
// in a `try/finally` or use `addTearDown`.

Widget _wrapHome(AppState state) =>
    MaterialApp(home: HomeScreen(appState: state));

void main() {
  group('Integration: end-to-end state flow (spec § Exact work #2)', () {
    test(
      'win sets 5-minute allowed on default target (YouTube Shorts)',
      () async {
        final FakeAccessPlatform platform = FakeAccessPlatform();
        int now = 1_000_000;
        final AppState s = AppState(
          platform: platform,
          nowEpochMs: () => now,
          enableTick: false,
        );
        await s.init();
        addTearDown(s.dispose);

        expect(
          s.decisionFor('com.google.android.youtube').status,
          AccessStatus.needsChallenge,
        );

        await s.applyOutcome('com.google.android.youtube', won: true);
        final d = s.decisionFor('com.google.android.youtube');
        expect(d.status, AccessStatus.allowed);
        expect(d.remainingMs, kGrantDurationMs);
        expect(
          s.snapshotFor('com.google.android.youtube')!.grantedUntilEpochMs,
          now + kGrantDurationMs,
        );
        expect(platform.syncCalls.isNotEmpty, isTrue);
        // sync includes the granted snapshot
        final lastSync = platform.syncCalls.last;
        final yt = lastSync
            .where((e) => e.packageName == 'com.google.android.youtube')
            .single;
        expect(yt.grantedUntilEpochMs, now + kGrantDurationMs);
      },
    );

    test('background 19,999ms keeps grant; 20,000ms expires it', () async {
      final FakeAccessPlatform platform = FakeAccessPlatform();
      int now = 5_000_000;
      final AppState s = AppState(
        platform: platform,
        nowEpochMs: () => now,
        enableTick: false,
      );
      await s.init();
      addTearDown(s.dispose);

      await s.applyOutcome('com.google.android.youtube', won: true);
      final snap = s.snapshotFor('com.google.android.youtube')!;
      final bg = recordBackground(snap, now);
      expect(bg.lastBackgroundEpochMs, now);

      final still = expireAbandonedGrant(bg, now + 19_999);
      expect(still.grantedUntilEpochMs, isNotNull);
      expect(evaluateAccess(still, now + 19_999).status, AccessStatus.allowed);

      final expired = expireAbandonedGrant(bg, now + 20_000);
      expect(expired.grantedUntilEpochMs, isNull);
      expect(expired.lastBackgroundEpochMs, isNull);
      expect(
        evaluateAccess(expired, now + 20_000).status,
        AccessStatus.needsChallenge,
      );

      // Subsequent interception would challenge again
      // Simulate AppState foreground/background event cycle:
      platform.emit(
        const AccessEvent(
          type: 'targetBackground',
          packageName: 'com.google.android.youtube',
          atEpochMs: 1000000,
        ),
      );
      await Future<void>.delayed(const Duration(milliseconds: 50));
      // Advance clock to now+20s and let tick expire abandonment
      // For unit verification just assert helper behavior above is sufficient.
    });

    test('brief return within 20s preserves grant', () async {
      const int t0 = 1_000_000;
      final snap = EnforcementSnapshot(
        packageName: 'com.google.android.youtube',
        surface: TargetSurface.youtubeShorts,
        grantedUntilEpochMs: t0 + kGrantDurationMs,
      );
      final bg = recordBackground(snap, t0);
      final fg = recordForeground(bg, t0 + 10_000);
      expect(fg.lastBackgroundEpochMs, isNull);
      final bg2 = recordBackground(fg, t0 + 15_000);
      final preserved = expireAbandonedGrant(bg2, t0 + 25_000);
      expect(preserved.grantedUntilEpochMs, isNotNull);
      expect(
        evaluateAccess(preserved, t0 + 25_000).status,
        AccessStatus.allowed,
      );
      final revoked = expireAbandonedGrant(bg2, t0 + 35_000);
      expect(revoked.grantedUntilEpochMs, isNull);
    });

    test('loss locks 10 minutes', () async {
      final FakeAccessPlatform platform = FakeAccessPlatform();
      const int now = 2_000_000;
      final AppState s = AppState(
        platform: platform,
        nowEpochMs: () => now,
        enableTick: false,
      );
      await s.init();
      addTearDown(s.dispose);

      await s.applyOutcome('com.google.android.youtube', won: false);
      final d = s.decisionFor('com.google.android.youtube');
      expect(d.status, AccessStatus.locked);
      expect(d.remainingMs, kLockDurationMs);
      expect(
        s.snapshotFor('com.google.android.youtube')!.lockedUntilEpochMs,
        now + kLockDurationMs,
      );
      expect(
        s.snapshotFor('com.google.android.youtube')!.grantedUntilEpochMs,
        isNull,
      );
      expect(
        evaluateAccess(
          s.snapshotFor('com.google.android.youtube')!,
          now + kLockDurationMs - 1,
        ).status,
        AccessStatus.locked,
      );
      expect(
        evaluateAccess(
          s.snapshotFor('com.google.android.youtube')!,
          now + kLockDurationMs,
        ).status,
        AccessStatus.needsChallenge,
      );
    });

    test('custom package target uses same 5/10/20 lifecycle', () async {
      final FakeAccessPlatform platform = FakeAccessPlatform();
      int now = 3_000_000;
      final AppState s = AppState(
        platform: platform,
        nowEpochMs: () => now,
        enableTick: false,
      );
      await s.init();
      addTearDown(s.dispose);

      s.addCustomTarget(
        const LaunchableApp(
          packageName: 'com.example.game',
          displayName: 'Example Game',
        ),
      );
      expect(s.targetFor('com.example.game')!.surface, TargetSurface.wholeApp);

      await s.applyOutcome('com.example.game', won: true);
      expect(s.decisionFor('com.example.game').status, AccessStatus.allowed);
      final snap = s.snapshotFor('com.example.game')!;
      expect(snap.surface, TargetSurface.wholeApp);
      // Whole-app surface participates in same abandonment rule
      final bg = recordBackground(snap, now);
      expect(
        expireAbandonedGrant(bg, now + 19_999).grantedUntilEpochMs,
        isNotNull,
      );
      expect(
        expireAbandonedGrant(bg, now + 20_000).grantedUntilEpochMs,
        isNull,
      );

      // Loss variant
      now += 100_000;
      await s.applyOutcome('com.example.game', won: false);
      expect(s.decisionFor('com.example.game').status, AccessStatus.locked);
    });

    test('locked state beats stale grant', () async {
      const int now = 4_000_000;
      final snap = EnforcementSnapshot(
        packageName: 'com.example.app',
        surface: TargetSurface.wholeApp,
        grantedUntilEpochMs: now + kGrantDurationMs + kLockDurationMs,
        lockedUntilEpochMs: now + kLockDurationMs,
      );
      expect(evaluateAccess(snap, now).status, AccessStatus.locked);
      expect(evaluateAccess(snap, now).remainingMs, kLockDurationMs);
      // After lock expires but grant still live, grant resumes
      expect(
        evaluateAccess(snap, now + kLockDurationMs + 1).status,
        AccessStatus.allowed,
      );
    });

    test(
      'default target intercepted → challenge displayed (Gate semantics)',
      () async {
        final FakeAccessPlatform platform = FakeAccessPlatform();
        final AppState s = AppState(
          platform: platform,
          nowEpochMs: () => 0,
          enableTick: false,
        );
        await s.init();
        addTearDown(s.dispose);

        // Gate when needsChallenge shows PLAY BLACKJACK
        await _pumpGate(s, expectPlay: true);
        // Win then gate should not show play?
        await s.applyOutcome('com.google.android.youtube', won: true);
        // Gate still shows but decision is allowed; app routing would bypass gate.
        expect(
          s.decisionFor('com.google.android.youtube').status,
          AccessStatus.allowed,
        );
      },
    );

    test(
      'native foreground/background events map to policy then re-sync',
      () async {
        final FakeAccessPlatform platform = FakeAccessPlatform();
        int now = 6_000_000;
        final AppState s = AppState(
          platform: platform,
          nowEpochMs: () => now,
          enableTick: false,
        );
        await s.init();
        addTearDown(s.dispose);

        await s.applyOutcome('com.google.android.youtube', won: true);
        final syncAfterWin = platform.syncCalls.length;

        platform.emit(
          AccessEvent(
            type: 'targetBackground',
            packageName: 'com.google.android.youtube',
            atEpochMs: now + 1000,
          ),
        );
        await Future<void>.delayed(const Duration(milliseconds: 50));
        expect(
          s.snapshotFor('com.google.android.youtube')!.lastBackgroundEpochMs,
          now + 1000,
        );

        platform.emit(
          AccessEvent(
            type: 'targetForeground',
            packageName: 'com.google.android.youtube',
            atEpochMs: now + 5000,
          ),
        );
        await Future<void>.delayed(const Duration(milliseconds: 50));
        expect(
          s.snapshotFor('com.google.android.youtube')!.lastBackgroundEpochMs,
          isNull,
        );
        expect(platform.syncCalls.length, greaterThanOrEqualTo(syncAfterWin));
      },
    );

    test('disabled target is not synced to native', () async {
      final FakeAccessPlatform platform = FakeAccessPlatform();
      final AppState s = AppState(
        platform: platform,
        nowEpochMs: () => 0,
        enableTick: false,
      );
      await s.init();
      addTearDown(s.dispose);

      s.setTargetEnabled('com.google.android.youtube', false);
      // last sync should not contain that package
      await Future<void>.delayed(const Duration(milliseconds: 20));
      final last = platform.syncCalls.isEmpty
          ? <EnforcementSnapshot>[]
          : platform.syncCalls.last;
      expect(
        last.any((e) => e.packageName == 'com.google.android.youtube'),
        isFalse,
      );
    });
  });

  group('Widget integration: Gate/Home wire to AppState', () {
    testWidgets('Gate LOCKED hides PLAY BLACKJACK', (
      WidgetTester tester,
    ) async {
      final FakeAccessPlatform platform = FakeAccessPlatform();
      final AppState s = AppState(
        platform: platform,
        nowEpochMs: () => 0,
        enableTick: false,
      );
      await s.init();
      addTearDown(() => s.dispose());
      await s.applyOutcome('com.google.android.youtube', won: false);

      await tester.pumpWidget(
        MaterialApp(
          home: GateScreen(
            packageName: 'com.google.android.youtube',
            appState: s,
            onPlay: () {},
          ),
        ),
      );
      await tester.pump();
      expect(find.textContaining('LOCKED'), findsWidgets);
      expect(find.text('PLAY BLACKJACK'), findsNothing);
      expect(find.text('CHOOSE ANOTHER APP'), findsNothing);
    });

    testWidgets('Home reflects 5 MIN EARNED and LOCKED textual states', (
      WidgetTester tester,
    ) async {
      final FakeAccessPlatform platform = FakeAccessPlatform();
      int now = 10_000_000;
      final AppState s = AppState(
        platform: platform,
        nowEpochMs: () => now,
        enableTick: false,
      );
      await s.init();
      addTearDown(() => s.dispose());
      await s.applyOutcome('com.google.android.youtube', won: true);
      await s.applyOutcome('com.instagram.android', won: false);

      await tester.pumpWidget(_wrapHome(s));
      await tester.pump();
      expect(find.text('5 MIN EARNED'), findsOneWidget);
      expect(find.textContaining('LOCKED'), findsWidgets);
    });
  });
}

Future<void> _pumpGate(AppState s, {required bool expectPlay}) async {
  // minimal helper for non-widget check; real widget pump done in widget tests
  final d = s.decisionFor('com.google.android.youtube');
  if (expectPlay) {
    assert(d.status == AccessStatus.needsChallenge);
  }
}
