import 'dart:convert' as convert;

import '../domain/target_config.dart';
import '../domain/target_surface.dart';
import 'key_value_store.dart';

export 'key_value_store.dart';

const String kTargetConfigKey = 'sonder.targetConfig';

/// Repository over [TargetConfig] with JSON persistence.
///
/// Invalid/missing stored data falls back to defaults and never throws.
class TargetRepository {
  final KeyValueStore _store;

  TargetRepository(this._store);

  Future<TargetConfig> load() async {
    final raw = await _store.read(kTargetConfigKey);
    if (raw == null || raw.trim().isEmpty) return TargetConfig();
    try {
      final decoded = convert.jsonDecode(raw);
      if (decoded is! List) return TargetConfig();
      return TargetConfig.fromJsonList(decoded);
    } catch (_) {
      return TargetConfig();
    }
  }

  Future<void> save(TargetConfig config) async {
    final jsonList = config.toJsonList();
    final encoded = convert.jsonEncode(jsonList);
    await _store.write(kTargetConfigKey, encoded);
  }

  Future<void> clear() async {
    await _store.delete(kTargetConfigKey);
  }
}

/// Production-ready repository alias — uses dart:convert via [TargetRepository].
class JsonTargetRepository extends TargetRepository {
  JsonTargetRepository(super.store);
}

/// Convenience alias so tests can use [JsonTargetRepository] directly.
typedef TargetRepositoryImpl = JsonTargetRepository;

/// Extension helpers for direct config manipulation through the repo.
extension TargetRepositoryOps on TargetRepository {
  Future<TargetConfig> addCustomTarget(
    String packageName,
    String displayName,
  ) async {
    final current = await load();
    final next = current.addCustom(
      packageName: packageName,
      displayName: displayName,
    );
    if (!identical(next, current)) {
      await save(next);
    }
    return next;
  }

  Future<TargetConfig> removeCustomTarget(String packageName) async {
    final current = await load();
    final next = current.removeCustom(packageName);
    if (!identical(next, current)) {
      await save(next);
    }
    return next;
  }

  Future<TargetConfig> setTargetEnabled(
    String packageName,
    TargetSurface surface,
    bool enabled,
  ) async {
    final current = await load();
    final next = current.setEnabled(packageName, surface, enabled);
    if (!identical(next, current)) {
      await save(next);
    }
    return next;
  }
}
