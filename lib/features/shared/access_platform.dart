import 'dart:async';
import 'package:flutter/services.dart';
import '../../domain/models.dart';

/// Narrow platform seam used by every feature surface.
/// Contract names are taken verbatim from `employ/plan.md` so native can be
/// swapped in without widget changes.
abstract class AccessPlatform {
  Stream<AccessEvent> get events;
  Future<void> syncSnapshots(List<EnforcementSnapshot> snapshots);
  Future<List<LaunchableApp>> listLaunchableApps();
  Future<void> openAccessibilitySettings();
  Future<EnforcementCapabilities> getEnforcementCapabilities();
}

/// Event payloads matching the plan's native-to-Flutter JSON shapes.
class AccessEvent {
  final String
  type; // targetIntercepted | targetForeground | targetBackground | serviceStateChanged
  final String? packageName;
  final String? surface;
  final int? atEpochMs;
  final bool? accessibilityEnabled;
  final bool? overlayAvailable;

  const AccessEvent({
    required this.type,
    this.packageName,
    this.surface,
    this.atEpochMs,
    this.accessibilityEnabled,
    this.overlayAvailable,
  });

  factory AccessEvent.fromMap(Map<dynamic, dynamic> m) {
    return AccessEvent(
      type: m['type'] as String,
      packageName: m['packageName'] as String?,
      surface: m['surface'] as String?,
      atEpochMs: m['atEpochMs'] as int?,
      accessibilityEnabled: m['accessibilityEnabled'] as bool?,
      overlayAvailable: m['overlayAvailable'] as bool?,
    );
  }
}

class EnforcementCapabilities {
  final bool accessibilityEnabled;
  final bool overlayAvailable;

  const EnforcementCapabilities({
    required this.accessibilityEnabled,
    required this.overlayAvailable,
  });

  factory EnforcementCapabilities.fromMap(Map<dynamic, dynamic> m) {
    return EnforcementCapabilities(
      accessibilityEnabled: (m['accessibilityEnabled'] as bool?) ?? false,
      overlayAvailable: (m['overlayAvailable'] as bool?) ?? false,
    );
  }
}

/// Real MethodChannel/EventChannel implementation. Kept tiny — widgets do not
/// call the channel directly.
class MethodChannelAccessPlatform implements AccessPlatform {
  static const String channelName = 'sonder/access';
  final MethodChannel _methods = const MethodChannel(channelName);
  final EventChannel _events = const EventChannel('$channelName/events');

  Stream<AccessEvent>? _cached;

  @override
  Stream<AccessEvent> get events {
    _cached ??= _events.receiveBroadcastStream().map((dynamic raw) {
      final Map<dynamic, dynamic> m = raw as Map<dynamic, dynamic>;
      return AccessEvent.fromMap(m);
    }).asBroadcastStream();
    return _cached!;
  }

  @override
  Future<void> syncSnapshots(List<EnforcementSnapshot> snapshots) {
    return _methods.invokeMethod<void>('syncSnapshots', <String, dynamic>{
      'snapshots': snapshots.map((e) => e.toJson()).toList(),
    });
  }

  @override
  Future<List<LaunchableApp>> listLaunchableApps() async {
    final dynamic raw = await _methods.invokeMethod<dynamic>(
      'listLaunchableApps',
      <String, dynamic>{},
    );
    final List<dynamic> list;
    if (raw is List) {
      list = raw;
    } else if (raw is Map) {
      list = (raw['apps'] as List<dynamic>?) ?? <dynamic>[];
    } else {
      list = <dynamic>[];
    }
    return list.map((dynamic e) {
      final Map<dynamic, dynamic> m = e as Map<dynamic, dynamic>;
      return LaunchableApp(
        packageName: m['packageName'] as String,
        displayName: m['displayName'] as String,
      );
    }).toList();
  }

  @override
  Future<void> openAccessibilitySettings() {
    return _methods.invokeMethod<void>(
      'openAccessibilitySettings',
      <String, dynamic>{},
    );
  }

  @override
  Future<EnforcementCapabilities> getEnforcementCapabilities() async {
    final dynamic raw = await _methods.invokeMethod<dynamic>(
      'getEnforcementCapabilities',
      <String, dynamic>{},
    );
    return EnforcementCapabilities.fromMap(raw as Map<dynamic, dynamic>);
  }
}
