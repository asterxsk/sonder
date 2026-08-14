import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'data/key_value_store.dart';
import 'data/onboarding_repository.dart';
import 'features/app/app_state.dart';
import 'app.dart';
import 'features/shared/access_platform.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final SharedPreferences preferences = await SharedPreferences.getInstance();
  final KeyValueStore store = SharedPreferencesStore(preferences);
  final AccessPlatform platform = MethodChannelAccessPlatform();
  final AppState appState = AppState(
    platform: platform,
    nowEpochMs: () => DateTime.now().toUtc().millisecondsSinceEpoch,
    store: store,
  );
  final ValueNotifier<ThemeMode> themeMode = ValueNotifier<ThemeMode>(
    ThemeMode.system,
  );
  runApp(
    SonderRoot(
      appState: appState,
      platform: platform,
      themeMode: themeMode,
      onboardingRepository: OnboardingRepository(store),
    ),
  );
  // The native plugin is attached by MainActivity while the Flutter engine
  // starts. Initialize after the first frame so startup sync reaches the
  // `sonder/access` channel instead of racing plugin registration.
  WidgetsBinding.instance.addPostFrameCallback((_) {
    appState.init();
  });
}
