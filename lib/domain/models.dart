// Canonical barrel for Sonder domain contracts.
// Owned by Job 02. Re-exports the individual domain files so that
// Job 04+ code importing `domain/models.dart` resolves to the same types
// as code importing the granular files. This keeps bridge serialisation
// in sync with the Android DataStore field names.

export 'target_surface.dart';
export 'access_status.dart';
export 'blocked_target.dart';
export 'enforcement_snapshot.dart';
export 'access_decision.dart';
export 'access_policy.dart';
export 'blackjack.dart';
export 'target_config.dart';

// Launchable app descriptor returned by `listLaunchableApps` on the
// platform channel. Lives here so both domain and platform layers can
// share it without a circular import.
class LaunchableApp {
  final String packageName;
  final String displayName;

  const LaunchableApp({required this.packageName, required this.displayName});

  Map<String, dynamic> toJson() => <String, dynamic>{
    'packageName': packageName,
    'displayName': displayName,
  };

  static LaunchableApp fromJson(Map<dynamic, dynamic> json) {
    return LaunchableApp(
      packageName: json['packageName'] as String? ?? '',
      displayName: json['displayName'] as String? ?? '',
    );
  }

  @override
  bool operator ==(Object other) =>
      other is LaunchableApp &&
      other.packageName == packageName &&
      other.displayName == displayName;

  @override
  int get hashCode => Object.hash(packageName, displayName);

  @override
  String toString() => 'LaunchableApp($packageName)';
}
