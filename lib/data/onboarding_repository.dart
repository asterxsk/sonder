import 'key_value_store.dart';

const String kOnboardingCompletedKey = 'sonder.onboardingCompleted';

class OnboardingRepository {
  final KeyValueStore _store;

  OnboardingRepository(this._store);

  Future<bool> loadCompleted() async {
    return (await _store.read(kOnboardingCompletedKey)) == 'true';
  }

  Future<void> saveCompleted() async {
    await _store.write(kOnboardingCompletedKey, 'true');
  }
}
