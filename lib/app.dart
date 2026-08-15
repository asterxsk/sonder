import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'data/key_value_store.dart';
import 'data/onboarding_repository.dart';
import 'design/tokens.dart';
import 'features/app/app_state.dart';
import 'features/onboarding/onboarding_shell.dart';
import 'features/shared/access_platform.dart';
import 'features/stats/stats_screen.dart';
import 'features/targets/targets_screen.dart';
import 'features/home/home_screen.dart';
import 'features/settings/settings_screen.dart';
import 'features/blackjack/blackjack_screen.dart';
import 'features/blackjack/result_screen.dart';
import 'features/gate/gate_screen.dart';

class SonderRoot extends StatefulWidget {
  final AppState appState;
  final AccessPlatform platform;
  final ValueNotifier<ThemeMode> themeMode;
  final OnboardingRepository onboardingRepository;

  SonderRoot({
    super.key,
    required this.appState,
    required this.platform,
    required this.themeMode,
    OnboardingRepository? onboardingRepository,
  }) : onboardingRepository =
           onboardingRepository ?? OnboardingRepository(InMemoryStore());

  @override
  State<SonderRoot> createState() => _SonderRootState();
}

class _SonderRootState extends State<SonderRoot> {
  bool _onboarded = false;
  bool _onboardingLoaded = false;

  @override
  void initState() {
    super.initState();
    _loadOnboarding();
  }

  Future<void> _loadOnboarding() async {
    final bool completed = await widget.onboardingRepository.loadCompleted();
    if (!mounted) return;
    setState(() {
      _onboarded = completed;
      _onboardingLoaded = true;
    });
  }

  Future<void> _finishOnboarding() async {
    await widget.onboardingRepository.saveCompleted();
    if (!mounted) return;
    setState(() => _onboarded = true);
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([widget.appState, widget.themeMode]),
      builder: (BuildContext context, _) {
        return MaterialApp(
          title: 'Sonder',
          theme: buildSonderTheme(isDark: false),
          darkTheme: buildSonderTheme(isDark: true),
          themeMode: widget.themeMode.value,
          home: !_onboardingLoaded
              ? const Scaffold(body: Center(child: CircularProgressIndicator()))
              : _onboarded
              ? DashboardShell(
                  appState: widget.appState,
                  platform: widget.platform,
                  themeMode: widget.themeMode,
                )
              : OnboardingShell(
                  onFinished: _finishOnboarding,
                  platform: widget.platform,
                  onOpenAccessibilitySettings: () async {
                    try {
                      await widget.platform.openAccessibilitySettings();
                    } catch (_) {}
                  },
                  onCheckCapabilities: widget.appState.refreshCapabilities,
                ),
        );
      },
    );
  }
}

class DashboardShell extends StatefulWidget {
  final AppState appState;
  final AccessPlatform platform;
  final ValueNotifier<ThemeMode> themeMode;

  const DashboardShell({
    super.key,
    required this.appState,
    required this.platform,
    required this.themeMode,
  });

  @override
  State<DashboardShell> createState() => _DashboardShellState();
}

class _DashboardShellState extends State<DashboardShell> {
  int _tab = 0;
  String? _handlingIntercept;

  @override
  Widget build(BuildContext context) {
    _openInterceptedGate(context);
    final List<Widget> pages = [
      HomeScreen(appState: widget.appState),
      StatsScreen(appState: widget.appState),
      TargetsScreen(appState: widget.appState, platform: widget.platform),
      SettingsScreen(
        appState: widget.appState,
        platform: widget.platform,
        themeMode: widget.themeMode,
      ),
    ];

    return Scaffold(
      body: IndexedStack(index: _tab, children: pages),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (int index) => setState(() => _tab = index),
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: 'HOME',
          ),
          NavigationDestination(
            icon: Icon(Icons.bar_chart_outlined),
            selectedIcon: Icon(Icons.bar_chart),
            label: 'STATS',
          ),
          NavigationDestination(
            icon: Icon(Icons.apps_outlined),
            selectedIcon: Icon(Icons.apps),
            label: 'APPS',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings),
            label: 'SETTINGS',
          ),
        ],
      ),
    );
  }

  void _openInterceptedGate(BuildContext context) {
    final String? packageName = widget.appState.interceptedPackage;
    if (packageName == null || packageName == _handlingIntercept) return;
    _handlingIntercept = packageName;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      widget.appState.consumeIntercepted();
      Navigator.of(context)
          .push(
            MaterialPageRoute<void>(
              builder: (_) => GateScreen(
                packageName: packageName,
                appState: widget.appState,
                onPlay: () => _openBlackjack(context, packageName),
                onChooseAnotherApp: () {
                  Navigator.of(context).pop();
                  setState(() => _tab = 2);
                },
              ),
            ),
          )
          .whenComplete(() => _handlingIntercept = null);
    });
  }

  void _openBlackjack(BuildContext context, String packageName) {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => BlackjackScreen(
          packageName: packageName,
          appState: widget.appState,
          onFinished: (bool won) async {
            await widget.appState.applyOutcome(packageName, won: won);
            if (!context.mounted) return;
            Navigator.of(context).pushReplacement(
              MaterialPageRoute<void>(
                builder: (_) => ResultScreen(
                  packageName: packageName,
                  won: won,
                  appState: widget.appState,
                  onDone: () {
                    // Return to the blocked app. The native overlay
                    // will re-evaluate: allowed (win) dismisses it,
                    // locked (loss) shows the timer.
                      Navigator.of(context).popUntil((r) => r.isFirst);
                    // Move Sonder to the back so the blocked app
                    // (or home) is in the foreground.
                      SystemNavigator.pop();
                    },
                ),
              ),
            );
          },
        ),
      ),
    );
  }
}
