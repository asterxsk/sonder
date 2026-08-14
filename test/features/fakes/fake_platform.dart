import 'dart:async';
import 'package:sonder/domain/models.dart';
import 'package:sonder/features/shared/access_platform.dart';

class FakeAccessPlatform implements AccessPlatform {
  final StreamController<AccessEvent> _controller =
      StreamController<AccessEvent>.broadcast();

  EnforcementCapabilities capabilitiesToReturn = const EnforcementCapabilities(
    accessibilityEnabled: false,
    overlayAvailable: false,
  );

  List<LaunchableApp> launchableAppsToReturn = const <LaunchableApp>[];
  bool openSettingsCalled = false;
  List<List<EnforcementSnapshot>> syncCalls = <List<EnforcementSnapshot>>[];
  bool throwOnListApps = false;

  @override
  Stream<AccessEvent> get events => _controller.stream;

  void emit(AccessEvent e) => _controller.add(e);

  @override
  Future<void> syncSnapshots(List<EnforcementSnapshot> snapshots) async {
    syncCalls.add(List<EnforcementSnapshot>.unmodifiable(snapshots));
  }

  @override
  Future<List<LaunchableApp>> listLaunchableApps() async {
    if (throwOnListApps) throw Exception('list fail');
    return launchableAppsToReturn;
  }

  @override
  Future<void> openAccessibilitySettings() async {
    openSettingsCalled = true;
  }

  @override
  Future<EnforcementCapabilities> getEnforcementCapabilities() async {
    return capabilitiesToReturn;
  }

  void dispose() => _controller.close();
}
