import 'blocked_target.dart';
import 'target_surface.dart';

/// Built-in targets enabled by default.
///
/// Exact package names and surfaces from the shared product contract.
const List<BlockedTarget> kDefaultTargets = [
  BlockedTarget(
    packageName: 'com.google.android.youtube',
    displayName: 'YouTube',
    surface: TargetSurface.youtubeShorts,
    enabled: true,
  ),
  BlockedTarget(
    packageName: 'com.instagram.android',
    displayName: 'Instagram',
    surface: TargetSurface.instagramReels,
    enabled: true,
  ),
];

String _targetKey(String packageName, TargetSurface surface) =>
    '$packageName:${surface.name}';

bool _isBuiltIn(String packageName, TargetSurface surface) => kDefaultTargets
    .any((t) => t.packageName == packageName && t.surface == surface);

/// Immutable configuration of all blocked targets.
///
/// Deduplication key is `packageName + surface`. Built-ins cannot be removed
/// but may be disabled via [setEnabled]. Custom targets are always
/// [TargetSurface.wholeApp].
class TargetConfig {
  final List<BlockedTarget> _targets;

  TargetConfig([List<BlockedTarget>? targets])
    : _targets = _dedupAndNormalize(targets ?? kDefaultTargets);

  /// Creates a config from an explicit list, deduplicating and ensuring
  /// built-ins are present (even if disabled state is preserved).
  factory TargetConfig.fromList(List<BlockedTarget> targets) {
    return TargetConfig(targets);
  }

  List<BlockedTarget> get all => List.unmodifiable(_targets);

  List<BlockedTarget> get enabled =>
      List.unmodifiable(_targets.where((t) => t.enabled));

  /// Adds a custom whole-app target. Returns a new config.
  ///
  /// If a target with the same package+surface already exists, the original
  /// config is returned unchanged. Package names are trimmed; empty names are
  /// ignored and the config is returned unchanged.
  TargetConfig addCustom({
    required String packageName,
    required String displayName,
  }) {
    final pkg = packageName.trim();
    final name = displayName.trim();
    if (pkg.isEmpty || name.isEmpty) return this;
    const surface = TargetSurface.wholeApp;
    final key = _targetKey(pkg, surface);
    if (_targets.any((t) => _targetKey(t.packageName, t.surface) == key)) {
      return this;
    }
    final next = List<BlockedTarget>.from(_targets)
      ..add(
        BlockedTarget(
          packageName: pkg,
          displayName: name,
          surface: surface,
          enabled: true,
        ),
      );
    return TargetConfig(next);
  }

  /// Removes a custom target by package name.
  ///
  /// Built-ins are never removed; if the key matches a built-in the config
  /// is returned unchanged. Returns a new config.
  TargetConfig removeCustom(String packageName) {
    final pkg = packageName.trim();
    const surface = TargetSurface.wholeApp;
    if (_isBuiltIn(pkg, surface)) return this;
    final key = _targetKey(pkg, surface);
    final next = _targets
        .where((t) => _targetKey(t.packageName, t.surface) != key)
        .toList();
    if (next.length == _targets.length) return this;
    return TargetConfig(next);
  }

  /// Sets enabled state for the target identified by [packageName] and
  /// [surface]. Returns a new config. If the target is not found, returns
  /// this unchanged.
  TargetConfig setEnabled(
    String packageName,
    TargetSurface surface,
    bool enabled,
  ) {
    final key = _targetKey(packageName, surface);
    var changed = false;
    final next = _targets.map((t) {
      if (_targetKey(t.packageName, t.surface) == key && t.enabled != enabled) {
        changed = true;
        return t.copyWith(enabled: enabled);
      }
      return t;
    }).toList();
    if (!changed) return this;
    return TargetConfig(next);
  }

  /// JSON list representation for SharedPreferences/Hive persistence.
  List<Map<String, dynamic>> toJsonList() =>
      _targets.map((t) => t.toJson()).toList();

  /// Parses a JSON list, dropping malformed entries and deduplicating.
  ///
  /// If the persisted data contains no valid entries, returns a config with
  /// default targets (fail-closed for blocking: defaults remain).
  static TargetConfig fromJsonList(List<dynamic>? jsonList) {
    if (jsonList == null || jsonList.isEmpty) {
      return TargetConfig();
    }
    final parsed = <BlockedTarget>[];
    for (final entry in jsonList) {
      if (entry is! Map<String, dynamic>) continue;
      final t = BlockedTarget.tryFromJson(entry);
      if (t != null) parsed.add(t);
    }
    if (parsed.isEmpty) return TargetConfig();
    // Ensure built-ins are present even if storage was wiped of them.
    // If a built-in is missing from persisted data, re-add it as enabled.
    for (final builtin in kDefaultTargets) {
      final key = _targetKey(builtin.packageName, builtin.surface);
      if (!parsed.any((t) => _targetKey(t.packageName, t.surface) == key)) {
        parsed.add(builtin);
      }
    }
    return TargetConfig(parsed);
  }

  static List<BlockedTarget> _dedupAndNormalize(List<BlockedTarget> input) {
    final seen = <String>{};
    final result = <BlockedTarget>[];
    for (final t in input) {
      final key = _targetKey(t.packageName, t.surface);
      if (seen.contains(key)) continue;
      seen.add(key);
      result.add(t);
    }
    // Ensure built-ins exist (re-add missing ones as enabled).
    for (final builtin in kDefaultTargets) {
      final key = _targetKey(builtin.packageName, builtin.surface);
      if (!seen.contains(key)) {
        seen.add(key);
        result.add(builtin);
      }
    }
    return result;
  }

  @override
  bool operator ==(Object other) {
    if (other is! TargetConfig) return false;
    if (other._targets.length != _targets.length) return false;
    for (var i = 0; i < _targets.length; i++) {
      if (other._targets[i] != _targets[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hashAll(_targets);
}
