import '../domain/enforcement_snapshot.dart';
import 'enforcement_codec.dart';
import 'target_repository.dart';

const String kSnapshotKeyPrefix = 'sonder.snapshot.';

String snapshotKey(String packageName, String surface) =>
    '$kSnapshotKeyPrefix$packageName:$surface';

String snapshotKeyFor(EnforcementSnapshot s) =>
    snapshotKey(s.packageName, s.surface.name);

/// Repository for per-target enforcement snapshots.
///
/// Each snapshot is stored under `sonder.snapshot.<package>:<surface>`.
/// Invalid/missing entries are treated as absent; callers should fail closed
/// (needsChallenge) for missing snapshots. Optionally, a single JSON list
/// key can be used for bulk sync with the native DataStore.
class SnapshotRepository {
  final KeyValueStore _store;
  final EnforcementCodec _codec;

  SnapshotRepository(this._store, [this._codec = const EnforcementCodec()]);

  /// Loads the snapshot for [packageName] + [surface].
  ///
  /// Returns null if missing or malformed (caller should treat as needsChallenge).
  Future<EnforcementSnapshot?> loadOne(
    String packageName,
    String surface,
  ) async {
    final raw = await _store.read(snapshotKey(packageName, surface));
    final snap = _codec.decodeOne(raw);
    if (snap == null && raw != null) {
      // Malformed stored data: remove it so the target fails closed cleanly.
      await _store.delete(snapshotKey(packageName, surface));
    }
    return snap;
  }

  Future<void> saveOne(EnforcementSnapshot snapshot) async {
    final key = snapshotKeyFor(snapshot);
    final encoded = _codec.encodeOne(snapshot);
    await _store.write(key, encoded);
  }

  Future<void> deleteOne(String packageName, String surface) async {
    await _store.delete(snapshotKey(packageName, surface));
  }

  /// Saves a list for the bulk channel sync path.
  Future<void> saveAll(List<EnforcementSnapshot> snapshots) async {
    for (final s in snapshots) {
      await saveOne(s);
    }
  }

  /// Loads all snapshots matching the snapshot prefix.
  ///
  /// Only available when the underlying store exposes enumeration. For
  /// [InMemoryStore] this works; for opaque stores it returns empty.
  Future<List<EnforcementSnapshot>> loadAll() async {
    final store = _store;
    if (store is InMemoryStore) {
      final result = <EnforcementSnapshot>[];
      for (final entry in store.debugData.entries) {
        if (!entry.key.startsWith(kSnapshotKeyPrefix)) continue;
        final snap = _codec.decodeOne(entry.value);
        if (snap != null) result.add(snap);
      }
      return result;
    }
    return [];
  }

  /// Channel helpers: encode snapshots for `syncSnapshots`.
  Map<String, dynamic> encodeForChannel(List<EnforcementSnapshot> snapshots) =>
      _codec.encodeForChannel(snapshots);

  /// Decodes a channel payload. Invalid entries are dropped; count is in
  /// [DecodeResult.invalidEntries] for logging.
  DecodeResult decodeChannelPayload(List<dynamic>? snapshotsJson) =>
      _codec.decodeDynamicList(snapshotsJson);
}
