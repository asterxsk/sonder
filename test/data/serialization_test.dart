import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/data/enforcement_codec.dart';
import 'package:sonder/domain/enforcement_snapshot.dart';
import 'package:sonder/domain/target_surface.dart';
import 'package:sonder/domain/blocked_target.dart';

void main() {
  group('EnforcementSnapshot serialization', () {
    test('toJson / tryFromJson round-trip', () {
      const snap = EnforcementSnapshot(
        packageName: 'com.example.app',
        surface: TargetSurface.wholeApp,
        grantedUntilEpochMs: 1000,
        lockedUntilEpochMs: null,
        lastBackgroundEpochMs: 500,
      );
      final json = snap.toJson();
      expect(json['packageName'], 'com.example.app');
      expect(json['surface'], 'wholeApp');
      expect(json['grantedUntilEpochMs'], 1000);
      expect(json['lockedUntilEpochMs'], isNull);

      final restored = EnforcementSnapshot.tryFromJson(json);
      expect(restored, snap);
    });

    test('tryFromJson returns null on missing packageName', () {
      final r = EnforcementSnapshot.tryFromJson({
        'surface': 'wholeApp',
        'grantedUntilEpochMs': null,
      });
      expect(r, isNull);
    });

    test('tryFromJson returns null on unknown surface', () {
      final r = EnforcementSnapshot.tryFromJson({
        'packageName': 'com.a',
        'surface': 'unknown',
      });
      expect(r, isNull);
    });

    test('tryFromJson returns null on non-int timestamps', () {
      final r = EnforcementSnapshot.tryFromJson({
        'packageName': 'com.a',
        'surface': 'wholeApp',
        'grantedUntilEpochMs': '1000',
      });
      expect(r, isNull);
    });

    test('tryFromJson returns null on negative timestamps', () {
      final r = EnforcementSnapshot.tryFromJson({
        'packageName': 'com.a',
        'surface': 'wholeApp',
        'grantedUntilEpochMs': -1,
      });
      expect(r, isNull);
    });

    test('missing optional fields default to null', () {
      final r = EnforcementSnapshot.tryFromJson({
        'packageName': 'com.a',
        'surface': 'youtubeShorts',
      });
      expect(r, isNotNull);
      expect(r!.grantedUntilEpochMs, isNull);
      expect(r.lockedUntilEpochMs, isNull);
      expect(r.lastBackgroundEpochMs, isNull);
    });

    test(
      'invalid stored data must never grant access (snapshot null => closed)',
      () {
        // Simulate repository returning null for malformed snapshot.
        final malformed = EnforcementSnapshot.tryFromJson({
          'packageName': '',
          'surface': 'wholeApp',
          'grantedUntilEpochMs': 999999999,
        });
        expect(
          malformed,
          isNull,
          reason: 'Malformed snapshot must be rejected',
        );
      },
    );
  });

  group('BlockedTarget serialization', () {
    test('round-trip', () {
      const t = BlockedTarget(
        packageName: 'com.example',
        displayName: 'Example',
        surface: TargetSurface.instagramReels,
        enabled: true,
      );
      final json = t.toJson();
      expect(BlockedTarget.tryFromJson(json), t);
    });

    test('invalid surface returns null', () {
      final r = BlockedTarget.tryFromJson({
        'packageName': 'com.a',
        'displayName': 'A',
        'surface': 'bad',
        'enabled': true,
      });
      expect(r, isNull);
    });
  });

  group('EnforcementCodec', () {
    const codec = EnforcementCodec();

    test('encodeList / decodeList round-trip', () {
      final snaps = [
        const EnforcementSnapshot(
          packageName: 'com.a',
          surface: TargetSurface.wholeApp,
          grantedUntilEpochMs: 1000,
        ),
        const EnforcementSnapshot(
          packageName: 'com.b',
          surface: TargetSurface.youtubeShorts,
        ),
      ];
      final encoded = codec.encodeList(snaps);
      final result = codec.decodeList(encoded);
      expect(result.valid, snaps);
      expect(result.invalidEntries, 0);
    });

    test('decodeList counts invalid entries without throwing', () {
      final mixed = jsonEncode([
        {
          'packageName': 'com.a',
          'surface': 'wholeApp',
          'grantedUntilEpochMs': 1000,
        },
        {'bad': 'data'},
        {'packageName': 'com.b', 'surface': 'unknown'},
      ]);
      final result = codec.decodeList(mixed);
      expect(result.valid.length, 1);
      expect(result.invalidEntries, 2);
    });

    test('decodeList on invalid JSON returns invalidEntries 1', () {
      final result = codec.decodeList('not json');
      expect(result.valid, isEmpty);
      expect(result.invalidEntries, 1);
    });

    test('decodeList on empty/null returns empty', () {
      expect(codec.decodeList(null).valid, isEmpty);
      expect(codec.decodeList('').valid, isEmpty);
      expect(codec.decodeList('[]').valid, isEmpty);
    });

    test('decodeOne returns null on malformed and never grants', () {
      expect(codec.decodeOne(null), isNull);
      expect(codec.decodeOne(''), isNull);
      expect(codec.decodeOne('not json'), isNull);
      expect(
        codec.decodeOne(jsonEncode({'packageName': '', 'surface': 'wholeApp'})),
        isNull,
      );
    });

    test('encodeForChannel produces snapshots key with exact field names', () {
      final map = codec.encodeForChannel([
        const EnforcementSnapshot(
          packageName: 'com.a',
          surface: TargetSurface.wholeApp,
          grantedUntilEpochMs: 123,
        ),
      ]);
      expect(map.containsKey('snapshots'), isTrue);
      final snap = (map['snapshots'] as List).first as Map<String, dynamic>;
      expect(snap.containsKey('packageName'), isTrue);
      expect(snap.containsKey('surface'), isTrue);
      expect(snap.containsKey('grantedUntilEpochMs'), isTrue);
      expect(snap.containsKey('lockedUntilEpochMs'), isTrue);
      expect(snap.containsKey('lastBackgroundEpochMs'), isTrue);
    });

    test('decodeDynamicList handles Map<dynamic,dynamic>', () {
      final list = <dynamic>[
        <dynamic, dynamic>{'packageName': 'com.a', 'surface': 'wholeApp'},
      ];
      final result = codec.decodeDynamicList(list);
      expect(result.valid.length, 1);
    });
  });
}
