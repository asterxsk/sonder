import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sonder/data/onboarding_repository.dart';
import 'package:sonder/data/target_repository.dart';
import 'package:sonder/domain/target_surface.dart';

void main() {
  test(
    'onboarding completion round-trips through the injected store',
    () async {
      final store = InMemoryStore();
      final repository = OnboardingRepository(store);

      expect(await repository.loadCompleted(), isFalse);
      await repository.saveCompleted();
      expect(await repository.loadCompleted(), isTrue);
    },
  );

  test(
    'shared preference store maps string values to the key-value contract',
    () async {
      SharedPreferences.setMockInitialValues(<String, Object>{});
      final store = SharedPreferencesStore(
        await SharedPreferences.getInstance(),
      );

      await store.write('key', 'value');
      expect(await store.read('key'), 'value');
      await store.delete('key');
      expect(await store.read('key'), isNull);
    },
  );

  test('target config persists custom apps and disabled states', () async {
    final store = InMemoryStore();
    final repository = TargetRepository(store);
    var config = await repository.load();
    config = config.addCustom(
      packageName: 'com.example.game',
      displayName: 'Game',
    );
    config = config.setEnabled(
      'com.google.android.youtube',
      TargetSurface.youtubeShorts,
      false,
    );
    await repository.save(config);

    final restored = await repository.load();
    expect(
      restored.all.any((target) => target.packageName == 'com.example.game'),
      isTrue,
    );
    expect(
      restored.enabled.any(
        (target) => target.packageName == 'com.google.android.youtube',
      ),
      isFalse,
    );
  });
}
