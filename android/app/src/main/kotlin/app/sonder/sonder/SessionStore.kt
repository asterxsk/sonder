package app.sonder.sonder

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "SonderSessionStore"

// Single DataStore instance per Context (process-wide).
private val Context.sonderDataStore by preferencesDataStore(name = "sonder_enforcement")

/**
 * Persistent mirror of the enforceable access state.
 *
 * Stored as a single JSON array string under [SNAPSHOTS_KEY] so the snapshot
 * list is atomically validated on every sync. Field names are the exact
 * shared contract names: packageName, surface, grantedUntilEpochMs,
 * lockedUntilEpochMs, lastBackgroundEpochMs.
 *
 * Any malformed/unknown snapshot in an incoming sync must fail closed for
 * that target: the whole incoming list is validated before it replaces the
 * stored list, so a partially-valid sync never leaves a temporary grant.
 */
class SessionStore(private val context: Context) {

    companion object {
        // Visible for tests.
        const val SNAPSHOTS_KEY_NAME = "sonder_snapshots_json"
        val SNAPSHOTS_KEY: Preferences.Key<String> = stringPreferencesKey(SNAPSHOTS_KEY_NAME)

        /**
         * Validates a raw snapshot list atomically. Returns null if the list
         * argument itself is malformed (not a list, etc.) or any entry is
         * malformed — callers must fail closed for all targets on null and
         * not replace stored state.
         *
         * The plan requires that syncSnapshots validates all records atomically
         * enough to avoid temporary grants. We satisfy this by validating the
         * entire incoming JSONArray before writing.
         */
        fun validateSnapshotsArray(jsonArray: JSONArray): List<EnforcementSnapshot>? {
            val parsed = mutableListOf<EnforcementSnapshot>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: return null
                val snap = EnforcementSnapshot.tryFromJson(obj) ?: return null
                parsed.add(snap)
            }
            return parsed
        }

        fun validateSnapshotsList(raw: List<*>): List<EnforcementSnapshot>? {
            val parsed = mutableListOf<EnforcementSnapshot>()
            for (entry in raw) {
                if (entry !is Map<*, *>) return null
                val snap = EnforcementSnapshot.tryFromMap(entry) ?: return null
                parsed.add(snap)
            }
            return parsed
        }
    }

    /** Flow of the current decoded snapshots (malformed stored entries are dropped). */
    val snapshotsFlow: Flow<List<EnforcementSnapshot>> =
        context.sonderDataStore.data.map { prefs ->
            val raw = prefs[SNAPSHOTS_KEY] ?: return@map emptyList()
            if (raw.isBlank()) return@map emptyList()
            try {
                val arr = JSONArray(raw)
                val out = mutableListOf<EnforcementSnapshot>()
                var invalid = 0
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i)
                    if (obj == null) { invalid++; continue }
                    val snap = EnforcementSnapshot.tryFromJson(obj)
                    if (snap == null) invalid++ else out.add(snap)
                }
                if (invalid > 0) {
                    Log.w(TAG, "Dropped $invalid malformed snapshot(s) on read (fail-closed per entry)")
                }
                out
            } catch (e: Exception) {
                Log.w(TAG, "Stored snapshots JSON malformed, returning empty (fail-closed)", e)
                emptyList()
            }
        }

    suspend fun readSnapshots(): List<EnforcementSnapshot> = snapshotsFlow.first()

    suspend fun readSnapshotsMap(): Map<String, EnforcementSnapshot> =
        readSnapshots().associateBy { keyFor(it.packageName, it.surface) }

    /**
     * Atomically validates then persists [snapshots].
     *
     * Returns true on success, false if validation failed (stored state left untouched).
     * All records are validated first; no partial write occurs.
     */
    suspend fun syncSnapshots(snapshots: List<Map<*, *>>): Boolean {
        val validated = validateSnapshotsList(snapshots) ?: run {
            Log.w(TAG, "syncSnapshots rejected: one or more entries malformed (fail-closed whole batch)")
            return false
        }
        // Serialise back to canonical JSON
        val arr = JSONArray()
        for (s in validated) arr.put(s.toJsonObject())
        context.sonderDataStore.edit { prefs ->
            prefs[SNAPSHOTS_KEY] = arr.toString()
        }
        return true
    }

    suspend fun syncSnapshotsTyped(snapshots: List<EnforcementSnapshot>) {
        val arr = JSONArray()
        for (s in snapshots) arr.put(s.toJsonObject())
        context.sonderDataStore.edit { prefs ->
            prefs[SNAPSHOTS_KEY] = arr.toString()
        }
    }

    suspend fun updateSnapshot(snapshot: EnforcementSnapshot) {
        val current = readSnapshots().toMutableList()
        val key = keyFor(snapshot.packageName, snapshot.surface)
        val idx = current.indexOfFirst { keyFor(it.packageName, it.surface) == key }
        if (idx >= 0) current[idx] = snapshot else current.add(snapshot)
        syncSnapshotsTyped(current)
    }

    suspend fun clear() {
        context.sonderDataStore.edit { prefs -> prefs.remove(SNAPSHOTS_KEY) }
    }

    fun keyFor(packageName: String, surface: TargetSurface): String =
        "$packageName::${surface.name}"
}
