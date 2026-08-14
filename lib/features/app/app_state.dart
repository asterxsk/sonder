import 'dart:async';
import 'package:flutter/foundation.dart';
import '../../data/target_repository.dart';
import '../../domain/models.dart';
import '../shared/access_platform.dart';
import 'stats_models.dart';

/// Single app-owned state object that bridges platform events, the access
/// policy free functions, and the Flutter UI. Widgets read snapshots and
/// decisions; they never compute policy themselves beyond view formatting.
///
/// Clock is injected via [nowEpochMs] so tests can freeze time.
class AppState extends ChangeNotifier {
  final AccessPlatform platform;
  final int Function() nowEpochMs;
  final TargetRepository targetRepository;

  // Canonical snapshot store keyed by packageName. Surface is retained per
  // package for display even though the bridge key is packageName.
  final Map<String, EnforcementSnapshot> _snapshots =
      <String, EnforcementSnapshot>{};
  final Map<String, BlockedTarget> _targets = <String, BlockedTarget>{};
  final Map<String, TargetStats> _statsByPackage = <String, TargetStats>{};
  int _interceptedOpens = 0;
  int _wins = 0;
  int _losses = 0;
  int _timeSavedMs = 0;
  int _lockedMs = 0;

  EnforcementCapabilities capabilities = const EnforcementCapabilities(
    accessibilityEnabled: false,
    overlayAvailable: false,
  );

  bool _loading = true;
  bool get loading => _loading;

  /// Last intercepted target, used to auto-open the gate.
  AccessEvent? lastIntercepted;
  String? get interceptedPackage => lastIntercepted?.packageName;

  StreamSubscription<AccessEvent>? _sub;
  Timer? _tick;
  final bool _enableTick;

  AppState({
    required this.platform,
    required this.nowEpochMs,
    KeyValueStore? store,
    TargetRepository? targetRepository,
    bool enableTick = true,
  }) : targetRepository =
           targetRepository ?? TargetRepository(store ?? InMemoryStore()),
       _enableTick = enableTick {
    for (final BlockedTarget t in kDefaultTargets) {
      _targets[t.packageName] = t;
      _snapshots[t.packageName] = EnforcementSnapshot(
        packageName: t.packageName,
        surface: t.surface,
      );
      _statsByPackage[t.packageName] = const TargetStats();
    }
  }

  List<BlockedTarget> get targets =>
      _targets.values.toList()
        ..sort((a, b) => a.displayName.compareTo(b.displayName));

  Map<String, EnforcementSnapshot> get snapshots =>
      Map.unmodifiable(_snapshots);

  StatsSnapshot get stats => StatsSnapshot(
    interceptedOpens: _interceptedOpens,
    wins: _wins,
    losses: _losses,
    timeSavedMs: _timeSavedMs,
    lockedMs: _lockedMs,
  );

  TargetStats statsFor(String packageName) =>
      _statsByPackage[packageName] ?? const TargetStats();

  /// Remaining earned time is the sum of live grants (non-locked, non-expired).
  int get totalGrantedRemainingMs {
    int sum = 0;
    final int now = nowEpochMs();
    for (final String pkg in _snapshots.keys) {
      final EnforcementSnapshot s = _snapshots[pkg]!;
      final AccessDecision d = evaluateAccess(s, now);
      if (d.status == AccessStatus.allowed) {
        sum += d.remainingMs ?? 0;
      }
    }
    return sum;
  }

  /// Soonest lockout remaining, if any.
  int? get soonestLockRemainingMs {
    int? best;
    final int now = nowEpochMs();
    for (final EnforcementSnapshot s in _snapshots.values) {
      final AccessDecision d = evaluateAccess(s, now);
      if (d.status == AccessStatus.locked) {
        final int r = d.remainingMs ?? 0;
        if (best == null || r < best) best = r;
      }
    }
    return best;
  }

  AccessDecision decisionFor(String packageName) {
    final EnforcementSnapshot? s = _snapshots[packageName];
    if (s == null) {
      return const AccessDecision(status: AccessStatus.needsChallenge);
    }
    return evaluateAccess(s, nowEpochMs());
  }

  EnforcementSnapshot? snapshotFor(String packageName) =>
      _snapshots[packageName];

  BlockedTarget? targetFor(String packageName) => _targets[packageName];

  Future<void> init() async {
    final TargetConfig config = await targetRepository.load();
    _targets
      ..clear()
      ..addEntries(
        config.all.map((target) => MapEntry(target.packageName, target)),
      );
    for (final BlockedTarget target in config.all) {
      _snapshots.putIfAbsent(
        target.packageName,
        () => EnforcementSnapshot(
          packageName: target.packageName,
          surface: target.surface,
        ),
      );
      _statsByPackage.putIfAbsent(
        target.packageName,
        () => const TargetStats(),
      );
    }

    try {
      capabilities = await platform.getEnforcementCapabilities();
    } catch (_) {
      capabilities = const EnforcementCapabilities(
        accessibilityEnabled: false,
        overlayAvailable: false,
      );
    }

    _sub = platform.events.listen((AccessEvent e) {
      if (e.type == 'serviceStateChanged') {
        capabilities = EnforcementCapabilities(
          accessibilityEnabled:
              e.accessibilityEnabled ?? capabilities.accessibilityEnabled,
          overlayAvailable: e.overlayAvailable ?? capabilities.overlayAvailable,
        );
        notifyListeners();
        return;
      }
      if (e.type == 'targetIntercepted') {
        lastIntercepted = e;
        _interceptedOpens += 1;
        final String? packageName = e.packageName;
        if (packageName != null) {
          final TargetStats previous = statsFor(packageName);
          _statsByPackage[packageName] = previous.copyWith(
            opens: previous.opens + 1,
          );
        }
        notifyListeners();
        return;
      }
      if (e.packageName == null || e.atEpochMs == null) return;
      final String pkg = e.packageName!;
      final int at = e.atEpochMs!;
      EnforcementSnapshot s =
          _snapshots[pkg] ??
          EnforcementSnapshot(
            packageName: pkg,
            surface: TargetSurface.wholeApp,
          );
      if (e.type == 'targetForeground') {
        _snapshots[pkg] = recordForeground(s, at);
        notifyListeners();
      } else if (e.type == 'targetBackground') {
        _snapshots[pkg] = recordBackground(s, at);
        notifyListeners();
      }
    }, onError: (_) {});

    // Sync defaults immediately. Without this, the native service has no
    // configured snapshots and cannot enforce the first launch.
    await _sync();

    // Periodic tick so lock/grant countdowns advance even without events.
    // Disabled in widget tests to avoid pending-timer failures; tests can
    // drive expiry deterministically via the pure `expireAbandonedGrant` helper.
    if (_enableTick) {
      _tick = Timer.periodic(const Duration(seconds: 1), (_) => _onTick());
    }

    _loading = false;
    notifyListeners();
  }

  void _onTick() {
    final int now = nowEpochMs();
    bool changed = false;
    for (final String pkg in _snapshots.keys.toList()) {
      final EnforcementSnapshot before = _snapshots[pkg]!;
      final EnforcementSnapshot after = expireAbandonedGrant(before, now);
      if (!identical(before, after)) {
        _snapshots[pkg] = after;
        changed = true;
      }
    }
    if (changed) notifyListeners();
    // Always tick UI for countdown rendering.
    notifyListeners();
  }

  Future<void> refreshCapabilities() async {
    try {
      capabilities = await platform.getEnforcementCapabilities();
      notifyListeners();
    } catch (_) {}
  }

  void addCustomTarget(LaunchableApp app) {
    if (_targets.containsKey(app.packageName)) return;
    _targets[app.packageName] = BlockedTarget(
      packageName: app.packageName,
      displayName: app.displayName,
      surface: TargetSurface.wholeApp,
      enabled: true,
    );
    _snapshots.putIfAbsent(
      app.packageName,
      () => EnforcementSnapshot(
        packageName: app.packageName,
        surface: TargetSurface.wholeApp,
      ),
    );
    unawaited(_persistAndSync());
    notifyListeners();
  }

  void removeTarget(String packageName) {
    _targets.remove(packageName);
    // Keep snapshot for enforcement consistency; just hide from list.
    unawaited(_persistAndSync());
    notifyListeners();
  }

  void setTargetEnabled(String packageName, bool enabled) {
    final BlockedTarget? t = _targets[packageName];
    if (t == null) return;
    _targets[packageName] = t.copyWith(enabled: enabled);
    unawaited(_persistAndSync());
    notifyListeners();
  }

  Future<void> _persistAndSync() async {
    await targetRepository.save(
      TargetConfig.fromList(_targets.values.toList()),
    );
    await _sync();
  }

  /// Apply win/loss outcome for a package and push to native.
  Future<void> applyOutcome(String packageName, {required bool won}) async {
    final int now = nowEpochMs();
    EnforcementSnapshot s =
        _snapshots[packageName] ??
        EnforcementSnapshot(
          packageName: packageName,
          surface: TargetSurface.wholeApp,
        );
    s = won ? applyWin(s, now) : applyLoss(s, now);
    _snapshots[packageName] = s;
    final TargetStats previous = statsFor(packageName);
    if (won) {
      _wins += 1;
      _timeSavedMs += kGrantDurationMs;
      _statsByPackage[packageName] = previous.copyWith(wins: previous.wins + 1);
    } else {
      _losses += 1;
      _lockedMs += kLockDurationMs;
      _statsByPackage[packageName] = previous.copyWith(
        losses: previous.losses + 1,
      );
    }
    await _sync();
    notifyListeners();
  }

  Future<void> _sync() async {
    try {
      // Only sync enabled targets; disabled ones remain locally but are not
      // enforced natively. Native can ignore missing packages.
      final List<EnforcementSnapshot> toSend = <EnforcementSnapshot>[];
      for (final String pkg in _targets.keys) {
        final BlockedTarget t = _targets[pkg]!;
        if (!t.enabled) continue;
        toSend.add(
          _snapshots[pkg] ??
              EnforcementSnapshot(packageName: pkg, surface: t.surface),
        );
      }
      await platform.syncSnapshots(toSend);
    } catch (_) {}
  }

  void consumeIntercepted() {
    lastIntercepted = null;
  }

  @override
  void dispose() {
    _sub?.cancel();
    _tick?.cancel();
    super.dispose();
  }
}
