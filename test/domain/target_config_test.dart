import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/domain/blocked_target.dart';
import 'package:sonder/domain/target_config.dart';
import 'package:sonder/domain/target_surface.dart';

void main() {
  group('defaults', () {
    test(
      'default targets contain YouTube Shorts and Instagram Reels enabled',
      () {
        final cfg = TargetConfig();
        expect(cfg.all.length, 2);
        expect(
          cfg.all.any(
            (t) =>
                t.packageName == 'com.google.android.youtube' &&
                t.surface == TargetSurface.youtubeShorts &&
                t.enabled,
          ),
          isTrue,
        );
        expect(
          cfg.all.any(
            (t) =>
                t.packageName == 'com.instagram.android' &&
                t.surface == TargetSurface.instagramReels &&
                t.enabled,
          ),
          isTrue,
        );
        expect(cfg.enabled.length, 2);
      },
    );

    test('fromList restores built-ins if missing', () {
      final cfg = TargetConfig.fromList([]);
      expect(cfg.all.length, 2);
    });

    test('fromList preserves built-in disabled state', () {
      final custom = [
        const BlockedTarget(
          packageName: 'com.google.android.youtube',
          displayName: 'YouTube',
          surface: TargetSurface.youtubeShorts,
          enabled: false,
        ),
        const BlockedTarget(
          packageName: 'com.instagram.android',
          displayName: 'Instagram',
          surface: TargetSurface.instagramReels,
          enabled: false,
        ),
      ];
      final cfg = TargetConfig.fromList(custom);
      expect(cfg.enabled, isEmpty);
      expect(cfg.all.length, 2);
    });
  });

  group('addCustom / removeCustom / dedup', () {
    test('addCustom adds wholeApp entry', () {
      final cfg = TargetConfig().addCustom(
        packageName: 'com.example.game',
        displayName: 'Example Game',
      );
      expect(cfg.all.length, 3);
      final added = cfg.all.where((t) => t.packageName == 'com.example.game');
      expect(added.length, 1);
      expect(added.first.surface, TargetSurface.wholeApp);
      expect(added.first.enabled, isTrue);
    });

    test('addCustom deduplicates same package+surface', () {
      var cfg = TargetConfig().addCustom(
        packageName: 'com.example.game',
        displayName: 'Example Game',
      );
      final cfg2 = cfg.addCustom(
        packageName: 'com.example.game',
        displayName: 'Example Game 2',
      );
      expect(identical(cfg, cfg2), isTrue);
      expect(cfg2.all.length, 3);
      // Original displayName preserved.
      expect(cfg2.all.last.displayName, 'Example Game');
    });

    test('addCustom ignores empty package or displayName', () {
      final cfg = TargetConfig();
      expect(
        identical(cfg, cfg.addCustom(packageName: '  ', displayName: 'X')),
        isTrue,
      );
      expect(
        identical(cfg, cfg.addCustom(packageName: 'com.x', displayName: '  ')),
        isTrue,
      );
    });

    test('removeCustom removes wholeApp entry', () {
      var cfg = TargetConfig().addCustom(
        packageName: 'com.example.game',
        displayName: 'Example Game',
      );
      cfg = cfg.removeCustom('com.example.game');
      expect(cfg.all.length, 2);
      expect(cfg.all.any((t) => t.packageName == 'com.example.game'), isFalse);
    });

    test('removeCustom does not remove built-ins', () {
      final cfg = TargetConfig();
      final cfg2 = cfg.removeCustom('com.google.android.youtube');
      expect(identical(cfg, cfg2), isTrue);
      expect(cfg2.all.length, 2);
    });

    test('dedup on construction by package+surface', () {
      final cfg = TargetConfig.fromList([
        ...kDefaultTargets,
        const BlockedTarget(
          packageName: 'com.example.game',
          displayName: 'A',
          surface: TargetSurface.wholeApp,
          enabled: true,
        ),
        const BlockedTarget(
          packageName: 'com.example.game',
          displayName: 'B',
          surface: TargetSurface.wholeApp,
          enabled: false,
        ),
      ]);
      // Duplicate removed => 3 total (2 built-ins + 1 custom)
      expect(cfg.all.length, 3);
      expect(
        cfg.all.where((t) => t.packageName == 'com.example.game').length,
        1,
      );
    });

    test('different surface is not deduped', () {
      final cfg = TargetConfig.fromList([
        ...kDefaultTargets,
        const BlockedTarget(
          packageName: 'com.example.game',
          displayName: 'Whole',
          surface: TargetSurface.wholeApp,
          enabled: true,
        ),
        const BlockedTarget(
          packageName: 'com.example.game',
          displayName: 'Shorts-like',
          surface: TargetSurface.youtubeShorts,
          enabled: true,
        ),
      ]);
      expect(
        cfg.all.where((t) => t.packageName == 'com.example.game').length,
        2,
      );
    });
  });

  group('setEnabled — built-ins may be disabled per policy', () {
    test('disable built-in keeps it but removes from enabled', () {
      var cfg = TargetConfig();
      cfg = cfg.setEnabled(
        'com.google.android.youtube',
        TargetSurface.youtubeShorts,
        false,
      );
      expect(cfg.all.length, 2);
      expect(cfg.enabled.length, 1);
      expect(
        cfg.all
            .firstWhere((t) => t.packageName == 'com.google.android.youtube')
            .enabled,
        isFalse,
      );
    });

    test('re-enable built-in', () {
      var cfg = TargetConfig().setEnabled(
        'com.google.android.youtube',
        TargetSurface.youtubeShorts,
        false,
      );
      cfg = cfg.setEnabled(
        'com.google.android.youtube',
        TargetSurface.youtubeShorts,
        true,
      );
      expect(cfg.enabled.length, 2);
    });

    test('setEnabled on missing target returns same instance', () {
      final cfg = TargetConfig();
      final cfg2 = cfg.setEnabled('com.missing', TargetSurface.wholeApp, false);
      expect(identical(cfg, cfg2), isTrue);
    });

    test('setEnabled no-op when already that value returns same instance', () {
      final cfg = TargetConfig();
      final cfg2 = cfg.setEnabled(
        'com.google.android.youtube',
        TargetSurface.youtubeShorts,
        true,
      );
      expect(identical(cfg, cfg2), isTrue);
    });
  });

  group('serialization', () {
    test('round-trip JSON list', () {
      final cfg = TargetConfig().addCustom(
        packageName: 'com.example.app',
        displayName: 'Example App',
      );
      final json = cfg.toJsonList();
      final restored = TargetConfig.fromJsonList(json);
      expect(restored.all.length, cfg.all.length);
      for (var i = 0; i < cfg.all.length; i++) {
        expect(restored.all[i], cfg.all[i]);
      }
    });

    test('fromJsonList drops malformed entries and restores built-ins', () {
      final cfg = TargetConfig.fromJsonList([
        {
          'packageName': 'com.a',
          'displayName': 'A',
          'surface': 'wholeApp',
          'enabled': true,
        },
        {
          'packageName': '',
          'displayName': 'Bad',
          'surface': 'wholeApp',
          'enabled': true,
        },
        {'bad': 'entry'},
        {
          'packageName': 'com.b',
          'displayName': 'B',
          'surface': 'wholeApp',
          'enabled': 'yes',
        },
      ]);
      // 1 valid custom + 2 restored built-ins = 3
      expect(cfg.all.any((t) => t.packageName == 'com.a'), isTrue);
      expect(cfg.all.any((t) => t.packageName == 'com.b'), isFalse);
      expect(cfg.all.length, 3);
    });

    test('fromJsonList null/empty returns defaults', () {
      expect(TargetConfig.fromJsonList(null).all.length, 2);
      expect(TargetConfig.fromJsonList([]).all.length, 2);
    });
  });
}
