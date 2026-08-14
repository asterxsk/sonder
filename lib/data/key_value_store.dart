import 'package:shared_preferences/shared_preferences.dart';

/// Small async string store used by local repositories.
abstract class KeyValueStore {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

/// In-memory implementation for tests.
class InMemoryStore implements KeyValueStore {
  final Map<String, String> _data = <String, String>{};

  @override
  Future<String?> read(String key) async => _data[key];

  @override
  Future<void> write(String key, String value) async {
    _data[key] = value;
  }

  @override
  Future<void> delete(String key) async {
    _data.remove(key);
  }

  Map<String, String> get debugData => Map.unmodifiable(_data);
}

/// SharedPreferences-backed production implementation.
class SharedPreferencesStore implements KeyValueStore {
  final SharedPreferences _preferences;

  SharedPreferencesStore(this._preferences);

  @override
  Future<String?> read(String key) async => _preferences.getString(key);

  @override
  Future<void> write(String key, String value) async {
    await _preferences.setString(key, value);
  }

  @override
  Future<void> delete(String key) async {
    await _preferences.remove(key);
  }
}
