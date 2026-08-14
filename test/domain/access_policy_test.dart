import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/domain/access_policy.dart';
import 'package:sonder/domain/access_status.dart';
import 'package:sonder/domain/enforcement_snapshot.dart';
import 'package:sonder/domain/target_surface.dart';

void main() {
  const now = 1_000_000;

  EnforcementSnapshot snap({
    int? grantedUntil,
    int? lockedUntil,
    int? lastBg,
    String pkg = 'com.example.app',
    TargetSurface surface = TargetSurface.wholeApp,
  }) => EnforcementSnapshot(
    packageName: pkg,
    surface: surface,
    grantedUntilEpochMs: grantedUntil,
    lockedUntilEpochMs: lockedUntil,
    lastBackgroundEpochMs: lastBg,
  );

  group('evaluateAccess', () {
    test('needsChallenge when no grant or lock', () {
      expect(evaluateAccess(snap(), now).status, AccessStatus.needsChallenge);
      expect(evaluateAccess(snap(), now).remainingMs, isNull);
    });

    test('locked when lockedUntil > now', () {
      final d = evaluateAccess(snap(lockedUntil: now + 600000), now);
      expect(d.status, AccessStatus.locked);
      expect(d.remainingMs, 600000);
    });

    test('not locked when lockedUntil == now (expired)', () {
      final d = evaluateAccess(snap(lockedUntil: now), now);
      expect(d.status, AccessStatus.needsChallenge);
    });

    test('not locked when lockedUntil < now', () {
      final d = evaluateAccess(snap(lockedUntil: now - 1), now);
      expect(d.status, AccessStatus.needsChallenge);
    });

    test('allowed when grantedUntil > now', () {
      final d = evaluateAccess(snap(grantedUntil: now + 300000), now);
      expect(d.status, AccessStatus.allowed);
      expect(d.remainingMs, 300000);
    });

    test('not allowed when grantedUntil == now (expired)', () {
      expect(
        evaluateAccess(snap(grantedUntil: now), now).status,
        AccessStatus.needsChallenge,
      );
    });

    test('lock wins over grant', () {
      final d = evaluateAccess(
        snap(grantedUntil: now + 300000, lockedUntil: now + 600000),
        now,
      );
      expect(d.status, AccessStatus.locked);
      expect(d.remainingMs, 600000);
    });

    test('grant resumes after lock expires', () {
      // Lock expired, grant still active.
      final d = evaluateAccess(
        snap(grantedUntil: now + 300000, lockedUntil: now - 1),
        now,
      );
      expect(d.status, AccessStatus.allowed);
    });

    test('expired grant and expired lock => needsChallenge', () {
      final d = evaluateAccess(
        snap(grantedUntil: now - 1, lockedUntil: now - 1),
        now,
      );
      expect(d.status, AccessStatus.needsChallenge);
    });

    test('5-minute grant boundary: remainingMs correct', () {
      // applyWin at now => grantedUntil = now + 300000, then evaluate at various times.
      final granted = applyWin(snap(), now);
      expect(granted.grantedUntilEpochMs, now + 300000);
      expect(
        evaluateAccess(granted, now + 299999).status,
        AccessStatus.allowed,
      );
      expect(
        evaluateAccess(granted, now + 300000).status,
        AccessStatus.needsChallenge,
      );
      expect(
        evaluateAccess(granted, now + 300001).status,
        AccessStatus.needsChallenge,
      );
    });

    test('10-minute lock boundary: remainingMs correct', () {
      final locked = applyLoss(snap(), now);
      expect(locked.lockedUntilEpochMs, now + 600000);
      expect(evaluateAccess(locked, now + 599999).status, AccessStatus.locked);
      expect(
        evaluateAccess(locked, now + 600000).status,
        AccessStatus.needsChallenge,
      );
    });
  });

  group('applyWin', () {
    test('sets grant to now+300000 and clears lock/background', () {
      final s0 = snap(lockedUntil: now + 1000, lastBg: now - 5000);
      final s1 = applyWin(s0, now);
      expect(s1.grantedUntilEpochMs, now + 300000);
      expect(s1.lockedUntilEpochMs, isNull);
      expect(s1.lastBackgroundEpochMs, isNull);
      expect(s1.packageName, s0.packageName);
      expect(s1.surface, s0.surface);
    });

    test('overwrites prior grant with new 5-minute window', () {
      final s0 = applyWin(snap(), now);
      final s1 = applyWin(s0, now + 60000);
      expect(s1.grantedUntilEpochMs, now + 60000 + 300000);
    });
  });

  group('applyLoss', () {
    test('sets lock to now+600000 and clears grant/background', () {
      final s0 = snap(grantedUntil: now + 1000, lastBg: now - 5000);
      final s1 = applyLoss(s0, now);
      expect(s1.lockedUntilEpochMs, now + 600000);
      expect(s1.grantedUntilEpochMs, isNull);
      expect(s1.lastBackgroundEpochMs, isNull);
    });

    test('overwrites prior lock with new 10-minute window', () {
      final s0 = applyLoss(snap(), now);
      final s1 = applyLoss(s0, now + 60000);
      expect(s1.lockedUntilEpochMs, now + 60000 + 600000);
    });
  });

  group('recordForeground / recordBackground / expireAbandonedGrant', () {
    test('recordBackground only while granted', () {
      final noGrant = snap();
      expect(identical(recordBackground(noGrant, now), noGrant), isTrue);

      final withGrant = snap(grantedUntil: now + 300000);
      final bg = recordBackground(withGrant, now);
      expect(bg.lastBackgroundEpochMs, now);
      expect(bg.grantedUntilEpochMs, now + 300000);
    });

    test('recordForeground clears background timer', () {
      final s0 = snap(grantedUntil: now + 300000, lastBg: now - 5000);
      final s1 = recordForeground(s0, now);
      expect(s1.lastBackgroundEpochMs, isNull);

      final s2 = snap(grantedUntil: now + 300000);
      expect(identical(recordForeground(s2, now), s2), isTrue);
    });

    test('expireAbandonedGrant at 19999 ms preserves grant', () {
      final s0 = snap(grantedUntil: now + 300000, lastBg: now);
      final s1 = expireAbandonedGrant(s0, now + 19999);
      expect(s1.grantedUntilEpochMs, isNotNull);
      expect(s1.lastBackgroundEpochMs, now);
    });

    test('expireAbandonedGrant at 20000 ms revokes grant', () {
      final s0 = snap(grantedUntil: now + 300000, lastBg: now);
      final s1 = expireAbandonedGrant(s0, now + 20000);
      expect(s1.grantedUntilEpochMs, isNull);
      expect(s1.lastBackgroundEpochMs, isNull);
      expect(
        evaluateAccess(s1, now + 20000).status,
        AccessStatus.needsChallenge,
      );
    });

    test('expireAbandonedGrant beyond 20000 ms also revokes', () {
      final s0 = snap(grantedUntil: now + 300000, lastBg: now);
      final s1 = expireAbandonedGrant(s0, now + 50000);
      expect(s1.grantedUntilEpochMs, isNull);
    });

    test('no background or no grant => no expiry', () {
      final s0 = snap(grantedUntil: now + 300000);
      expect(identical(expireAbandonedGrant(s0, now + 50000), s0), isTrue);

      final s1 = snap(lastBg: now);
      expect(identical(expireAbandonedGrant(s1, now + 50000), s1), isTrue);
    });

    test('brief return within threshold preserves grant', () {
      // Background at T, return at T+10s, check at T+25s total still allowed.
      final granted = snap(grantedUntil: now + 300000);
      final bg = recordBackground(granted, now);
      final fg = recordForeground(bg, now + 10000);
      // Now background again at T+15s
      final bg2 = recordBackground(fg, now + 15000);
      // At T+25s (10s since last background) still allowed.
      final checked = expireAbandonedGrant(bg2, now + 25000);
      expect(checked.grantedUntilEpochMs, isNotNull);
      expect(evaluateAccess(checked, now + 25000).status, AccessStatus.allowed);
      // At T+35s (20s since last background) revoked.
      final revoked = expireAbandonedGrant(bg2, now + 35000);
      expect(revoked.grantedUntilEpochMs, isNull);
    });

    test('full flow: win, background, abandonment, then needsChallenge', () {
      var s = snap();
      s = applyWin(s, now);
      expect(evaluateAccess(s, now).status, AccessStatus.allowed);
      s = recordBackground(s, now + 5000);
      // 19s later still allowed
      var checked = expireAbandonedGrant(s, now + 5000 + 19999);
      expect(
        evaluateAccess(checked, now + 5000 + 19999).status,
        AccessStatus.allowed,
      );
      // 20s exactly => revoked
      checked = expireAbandonedGrant(s, now + 5000 + 20000);
      expect(
        evaluateAccess(checked, now + 5000 + 20000).status,
        AccessStatus.needsChallenge,
      );
    });
  });
}
