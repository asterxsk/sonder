/// Target surface granularity.
///
/// Matches shared contract enum cases exactly.
enum TargetSurface { youtubeShorts, instagramReels, wholeApp }

/// JSON string values must match enum case names exactly.
extension TargetSurfaceJson on TargetSurface {
  String toJsonValue() => name;

  static TargetSurface? fromJsonValue(String? value) {
    if (value == null) return null;
    for (final v in TargetSurface.values) {
      if (v.name == value) return v;
    }
    return null;
  }
}
