import 'package:flutter/material.dart';
import '../blackjack/blackjack_screen.dart';
import '../blackjack/result_screen.dart';
import '../gate/gate_screen.dart';
import '../home/home_screen.dart';
import '../settings/settings_screen.dart';
import '../shared/access_platform.dart';
import '../shared/tokens.dart';
import '../targets/targets_screen.dart';
import 'app_state.dart';

class SonderApp extends StatefulWidget {
  final AppState appState;
  final AccessPlatform platform;
  final ValueNotifier<ThemeMode> themeMode;

  const SonderApp({
    super.key,
    required this.appState,
    required this.platform,
    required this.themeMode,
  });

  @override
  State<SonderApp> createState() => _SonderAppState();
}

class _SonderAppState extends State<SonderApp> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: Listenable.merge([widget.appState, widget.themeMode]),
      builder: (BuildContext context, _) {
        // Auto-open gate when native reports an interception.
        final String? intercepted = widget.appState.interceptedPackage;
        if (intercepted != null) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            if (!mounted) return;
            widget.appState.consumeIntercepted();
            Navigator.of(context).push(
              MaterialPageRoute<void>(
                builder: (_) => GateScreen(
                  packageName: intercepted,
                  appState: widget.appState,
                  onPlay: () {
                    Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) => BlackjackScreen(
                          packageName: intercepted,
                          appState: widget.appState,
                          onFinished: (bool won) async {
                            await widget.appState.applyOutcome(
                              intercepted,
                              won: won,
                            );
                            if (!context.mounted) return;
                            Navigator.of(context).pushReplacement(
                              MaterialPageRoute<void>(
                                builder: (_) => ResultScreen(
                                  packageName: intercepted,
                                  won: won,
                                  appState: widget.appState,
                                  onDone: () => Navigator.of(
                                    context,
                                  ).popUntil((r) => r.isFirst),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    );
                  },
                  onChooseAnotherApp: () {
                    Navigator.of(context).pop();
                    setState(() => _tab = 1);
                  },
                ),
              ),
            );
          });
        }

        final Widget body = switch (_tab) {
          0 => HomeScreen(appState: widget.appState),
          1 => TargetsScreen(
            appState: widget.appState,
            platform: widget.platform,
          ),
          _ => SettingsScreen(
            appState: widget.appState,
            platform: widget.platform,
            themeMode: widget.themeMode,
          ),
        };

        return MaterialApp(
          title: 'Sonder',
          theme: buildSonderTheme(isDark: false),
          darkTheme: buildSonderTheme(isDark: true),
          themeMode: widget.themeMode.value,
          home: Scaffold(
            body: body,
            bottomNavigationBar: _SonderBottomNav(
              index: _tab,
              onChanged: (int i) => setState(() => _tab = i),
            ),
          ),
        );
      },
    );
  }
}

class _SonderBottomNav extends StatelessWidget {
  final int index;
  final ValueChanged<int> onChanged;
  const _SonderBottomNav({required this.index, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return NavigationBar(
      selectedIndex: index,
      onDestinationSelected: onChanged,
      backgroundColor: theme.scaffoldBackgroundColor,
      indicatorColor: theme.colorScheme.primary.withValues(alpha: 0.14),
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.home_outlined),
          selectedIcon: Icon(Icons.home),
          label: 'Home',
        ),
        NavigationDestination(
          icon: Icon(Icons.list_alt_outlined),
          selectedIcon: Icon(Icons.list_alt),
          label: 'Targets',
        ),
        NavigationDestination(
          icon: Icon(Icons.settings_outlined),
          selectedIcon: Icon(Icons.settings),
          label: 'Settings',
        ),
      ],
    );
  }
}
