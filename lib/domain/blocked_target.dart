import 'target_surface.dart';

/// A configured blocking target.
///
/// Field names match the shared contract JSON keys via the serializer.
class BlockedTarget {
  /// Built-in defaults mandated by the product contract. Aliases
  /// [kDefaultTargets] so code importing via `models.dart` can use
  /// `BlockedTarget.defaults` without depending on `target_config.dart`.
  static const List<BlockedTarget> defaults = <BlockedTarget>[
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

  final String packageName;
  final String displayName;
  final TargetSurface surface;
  final bool enabled;

  const BlockedTarget({
    required this.packageName,
    required this.displayName,
    required this.surface,
    required this.enabled,
  });

  BlockedTarget copyWith({
    String? packageName,
    String? displayName,
    TargetSurface? surface,
    bool? enabled,
  }) {
    return BlockedTarget(
      packageName: packageName ?? this.packageName,
      displayName: displayName ?? this.displayName,
      surface: surface ?? this.surface,
      enabled: enabled ?? this.enabled,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
    'packageName': packageName,
    'displayName': displayName,
    'surface': surface.toJsonValue(),
    'enabled': enabled,
  };

  /// Returns null on invalid/malformed input (never throws).
  static BlockedTarget? tryFromJson(Map<String, dynamic> json) {
    final packageName = json['packageName'];
    final displayName = json['displayName'];
    final surfaceRaw = json['surface'];
    final enabled = json['enabled'];
    if (packageName is! String || packageName.isEmpty) return null;
    if (displayName is! String || displayName.isEmpty) return null;
    if (surfaceRaw is! String) return null;
    final surface = TargetSurfaceJson.fromJsonValue(surfaceRaw);
    if (surface == null) return null;
    if (enabled is! bool) return null;
    return BlockedTarget(
      packageName: packageName,
      displayName: displayName,
      surface: surface,
      enabled: enabled,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is BlockedTarget &&
      other.packageName == packageName &&
      other.displayName == displayName &&
      other.surface == surface &&
      other.enabled == enabled;

  @override
  int get hashCode => Object.hash(packageName, displayName, surface, enabled);

  @override
  String toString() =>
      'BlockedTarget($packageName, $surface, enabled=$enabled)';
}
