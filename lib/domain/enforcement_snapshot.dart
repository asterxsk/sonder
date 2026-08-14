import 'target_surface.dart';

/// Enforcement snapshot for one target package+surface.
///
/// Exact field names from the shared contract. Unknown/malformed snapshots
/// must fail closed for that target.
class EnforcementSnapshot {
  final String packageName;
  final TargetSurface surface;
  final int? grantedUntilEpochMs;
  final int? lockedUntilEpochMs;
  final int? lastBackgroundEpochMs;

  const EnforcementSnapshot({
    required this.packageName,
    required this.surface,
    this.grantedUntilEpochMs,
    this.lockedUntilEpochMs,
    this.lastBackgroundEpochMs,
  });

  EnforcementSnapshot copyWith({
    String? packageName,
    TargetSurface? surface,
    int? grantedUntilEpochMs,
    int? lockedUntilEpochMs,
    int? lastBackgroundEpochMs,
    bool clearGrantedUntil = false,
    bool clearLockedUntil = false,
    bool clearLastBackground = false,
  }) {
    return EnforcementSnapshot(
      packageName: packageName ?? this.packageName,
      surface: surface ?? this.surface,
      grantedUntilEpochMs: clearGrantedUntil
          ? null
          : (grantedUntilEpochMs ?? this.grantedUntilEpochMs),
      lockedUntilEpochMs: clearLockedUntil
          ? null
          : (lockedUntilEpochMs ?? this.lockedUntilEpochMs),
      lastBackgroundEpochMs: clearLastBackground
          ? null
          : (lastBackgroundEpochMs ?? this.lastBackgroundEpochMs),
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    'packageName': packageName,
    'surface': surface.toJsonValue(),
    'grantedUntilEpochMs': grantedUntilEpochMs,
    'lockedUntilEpochMs': lockedUntilEpochMs,
    'lastBackgroundEpochMs': lastBackgroundEpochMs,
  };

  /// Returns null on invalid/malformed input so callers can fail closed.
  static EnforcementSnapshot? tryFromJson(Map<String, dynamic> json) {
    final packageName = json['packageName'];
    final surfaceRaw = json['surface'];
    if (packageName is! String || packageName.isEmpty) return null;
    if (surfaceRaw is! String) return null;
    final surface = TargetSurfaceJson.fromJsonValue(surfaceRaw);
    if (surface == null) return null;

    int? grantedUntil;
    int? lockedUntil;
    int? lastBackground;

    if (json.containsKey('grantedUntilEpochMs')) {
      final v = json['grantedUntilEpochMs'];
      if (v != null && v is! int) return null;
      grantedUntil = v as int?;
    }
    if (json.containsKey('lockedUntilEpochMs')) {
      final v = json['lockedUntilEpochMs'];
      if (v != null && v is! int) return null;
      lockedUntil = v as int?;
    }
    if (json.containsKey('lastBackgroundEpochMs')) {
      final v = json['lastBackgroundEpochMs'];
      if (v != null && v is! int) return null;
      lastBackground = v as int?;
    }

    // Reject negative epoch values as malformed.
    if (grantedUntil != null && grantedUntil < 0) return null;
    if (lockedUntil != null && lockedUntil < 0) return null;
    if (lastBackground != null && lastBackground < 0) return null;

    return EnforcementSnapshot(
      packageName: packageName,
      surface: surface,
      grantedUntilEpochMs: grantedUntil,
      lockedUntilEpochMs: lockedUntil,
      lastBackgroundEpochMs: lastBackground,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is EnforcementSnapshot &&
      other.packageName == packageName &&
      other.surface == surface &&
      other.grantedUntilEpochMs == grantedUntilEpochMs &&
      other.lockedUntilEpochMs == lockedUntilEpochMs &&
      other.lastBackgroundEpochMs == lastBackgroundEpochMs;

  @override
  int get hashCode => Object.hash(
    packageName,
    surface,
    grantedUntilEpochMs,
    lockedUntilEpochMs,
    lastBackgroundEpochMs,
  );

  @override
  String toString() =>
      'EnforcementSnapshot($packageName/$surface, granted=$grantedUntilEpochMs, locked=$lockedUntilEpochMs, bg=$lastBackgroundEpochMs)';
}
