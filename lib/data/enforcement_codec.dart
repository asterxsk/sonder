import 'dart:convert';

import '../domain/enforcement_snapshot.dart';

/// JSON codec for the `sonder/access` platform channel snapshot list.
///
/// The channel field names must remain exactly:
/// `packageName`, `surface`, `grantedUntilEpochMs`,
/// `lockedUntilEpochMs`, `lastBackgroundEpochMs`.
///
/// Invalid/missing stored entries produce a closed (no-grant) result and are
/// surfaced via [DecodeResult.invalidEntries] for logging without personal
/// content.
class EnforcementCodec {
  const EnforcementCodec();

  /// Encodes [snapshots] as a JSON string suitable for DataStore / channel
  /// `syncSnapshots` arguments.
  String encodeList(List<EnforcementSnapshot> snapshots) {
    final list = snapshots.map((s) => s.toJson()).toList();
    return jsonEncode(list);
  }

  /// Encodes snapshots as the channel argument map.
  Map<String, dynamic> encodeForChannel(List<EnforcementSnapshot> snapshots) {
    return {'snapshots': snapshots.map((s) => s.toJson()).toList()};
  }

  /// Decodes a JSON string produced by [encodeList].
  ///
  /// Malformed entries are skipped and counted in [DecodeResult.invalidEntries].
  /// Never throws; returns empty valid list on fully invalid input.
  DecodeResult decodeList(String? jsonString) {
    if (jsonString == null || jsonString.trim().isEmpty) {
      return const DecodeResult(valid: [], invalidEntries: 0);
    }
    try {
      final decoded = jsonDecode(jsonString);
      if (decoded is! List) {
        return const DecodeResult(valid: [], invalidEntries: 1);
      }
      return _decodeDynamicList(decoded);
    } catch (_) {
      return const DecodeResult(valid: [], invalidEntries: 1);
    }
  }

  /// Decodes a dynamic list (e.g. from platform channel JSON).
  DecodeResult decodeDynamicList(List<dynamic>? list) {
    if (list == null) return const DecodeResult(valid: [], invalidEntries: 0);
    return _decodeDynamicList(list);
  }

  /// Decodes raw JSON maps (already parsed) into snapshots.
  DecodeResult decodeMaps(List<Map<String, dynamic>> maps) {
    return _decodeDynamicList(maps);
  }

  DecodeResult _decodeDynamicList(List<dynamic> list) {
    final valid = <EnforcementSnapshot>[];
    var invalid = 0;
    for (final entry in list) {
      if (entry is! Map<String, dynamic>) {
        // jsonDecode produces Map<String,dynamic> but platform may send
        // Map<dynamic,dynamic>.
        if (entry is Map) {
          try {
            final cast = entry.cast<String, dynamic>();
            final snap = EnforcementSnapshot.tryFromJson(cast);
            if (snap != null) {
              valid.add(snap);
            } else {
              invalid++;
            }
            continue;
          } catch (_) {
            invalid++;
            continue;
          }
        }
        invalid++;
        continue;
      }
      final snap = EnforcementSnapshot.tryFromJson(entry);
      if (snap != null) {
        valid.add(snap);
      } else {
        invalid++;
      }
    }
    return DecodeResult(valid: valid, invalidEntries: invalid);
  }

  /// Single-snapshot JSON encode/decode for per-key storage.
  String encodeOne(EnforcementSnapshot snapshot) =>
      jsonEncode(snapshot.toJson());

  /// Returns null on malformed input (fail closed).
  EnforcementSnapshot? decodeOne(String? jsonString) {
    if (jsonString == null || jsonString.trim().isEmpty) return null;
    try {
      final decoded = jsonDecode(jsonString);
      if (decoded is! Map<String, dynamic>) {
        if (decoded is Map) {
          return EnforcementSnapshot.tryFromJson(
            decoded.cast<String, dynamic>(),
          );
        }
        return null;
      }
      return EnforcementSnapshot.tryFromJson(decoded);
    } catch (_) {
      return null;
    }
  }
}

class DecodeResult {
  final List<EnforcementSnapshot> valid;
  final int invalidEntries;

  const DecodeResult({required this.valid, required this.invalidEntries});
}
