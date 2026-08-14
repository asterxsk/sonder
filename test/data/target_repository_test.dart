import 'package:flutter_test/flutter_test.dart';
import 'package:sonder/data/snapshot_repository.dart';
import 'package:sonder/data/target_repository.dart';
import 'package:sonder/domain/enforcement_snapshot.dart';
import 'package:sonder/domain/target_surface.dart';

void main() {
  group('TargetRepository with InMemoryStore', () {
    test('load returns defaults when empty', () async {
      final store = InMemoryStore();
      final repo = TargetRepository(store);
      final cfg = await repo.load();
      expect(cfg.all.length, 2);
      expect(cfg.enabled.length, 2);
    });

    test('save and load round-trip with custom', () async {
      final store = InMemoryStore();
      final repo = TargetRepository(store);
      var cfg = await repo.load();
      cfg = cfg.addCustom(packageName: 'com.example.game', displayName: 'Game');
      await repo.save(cfg);
      final restored = await repo.load();
      expect(restored.all.length, 3);
      expect(
        restored.all.any((t) => t.packageName == 'com.example.game'),
        isTrue,
      );
    });

    test('invalid stored data falls back to defaults', () async {
      final store = InMemoryStore();
      await store.write(kTargetConfigKey, 'not json at all');
      final repo = TargetRepository(store);
      final cfg = await repo.load();
      expect(cfg.all.length, 2);
    });

    test('non-list JSON returns defaults', () async {
      final store = InMemoryStore();
      await store.write(kTargetConfigKey, '{"not":"a list"}');
      final repo = TargetRepository(store);
      final cfg = await repo.load();
      expect(cfg.all.length, 2);
    });

    test('clear removes stored key', () async {
      final store = InMemoryStore();
      final repo = TargetRepository(store);
      await repo.save(
        (await repo.load()).addCustom(
          packageName: 'com.example.game',
          displayName: 'Game',
        ),
      );
      await repo.clear();
      final cfg = await repo.load();
      expect(cfg.all.length, 2);
    });

    test(
      'ops: addCustomTarget / removeCustomTarget / setTargetEnabled',
      () async {
        final store = InMemoryStore();
        final repo = TargetRepository(store);
        await repo.addCustomTarget('com.example.app', 'Example');
        var cfg = await repo.load();
        expect(cfg.all.length, 3);

        await repo.setTargetEnabled(
          'com.google.android.youtube',
          TargetSurface.youtubeShorts,
          false,
        );
        cfg = await repo.load();
        expect(cfg.enabled.length, 2); // instagram + custom

        await repo.removeCustomTarget('com.example.app');
        cfg = await repo.load();
        expect(cfg.all.length, 2);
      },
    );
  });

  group('SnapshotRepository', () {
    test('saveOne and loadOne round-trip, malformed deleted', () async {
      final store = InMemoryStore();
      final repo = SnapshotRepository(store);
      const snap = EnforcementSnapshot(
        packageName: 'com.a',
        surface: TargetSurface.wholeApp,
        grantedUntilEpochMs: 12345,
      );
      await repo.saveOne(snap);
      final loaded = await repo.loadOne('com.a', 'wholeApp');
      expect(loaded, snap);

      // Write malformed directly over the same key.
      await store.write('sonder.snapshot.com.a:wholeApp', 'not json');
      final malformed = await repo.loadOne('com.a', 'wholeApp');
      expect(malformed, isNull);
      // Key should be deleted for fail-closed cleanliness.
      expect(await store.read('sonder.snapshot.com.a:wholeApp'), isNull);
    });

    test('loadAll enumerates InMemoryStore entries', () async {
      final store = InMemoryStore();
      final repo = SnapshotRepository(store);
      const s1 = EnforcementSnapshot(
        packageName: 'com.a',
        surface: TargetSurface.wholeApp,
        grantedUntilEpochMs: 1,
      );
      const s2 = EnforcementSnapshot(
        packageName: 'com.b',
        surface: TargetSurface.youtubeShorts,
      );
      await repo.saveAll([s1, s2]);
      final all = await repo.loadAll();
      expect(all.length, 2);
      expect(all, contains(s1));
      expect(all, contains(s2));
    });

    test('saveAll then encodeForChannel has exact field names', () async {
      final store = InMemoryStore();
      final repo = SnapshotRepository(store);
      const s = EnforcementSnapshot(
        packageName: 'com.a',
        surface: TargetSurface.instagramReels,
        grantedUntilEpochMs: 999,
        lockedUntilEpochMs: null,
        lastBackgroundEpochMs: 100,
      );
      final map = repo.encodeForChannel([s]);
      final snap = (map['snapshots'] as List).first as Map<String, dynamic>;
      expect(snap['packageName'], 'com.a');
      expect(snap['surface'], 'instagramReels');
      expect(snap['grantedUntilEpochMs'], 999);
      expect(snap['lockedUntilEpochMs'], isNull);
      expect(snap['lastBackgroundEpochMs'], 100);
    });
  });
}
