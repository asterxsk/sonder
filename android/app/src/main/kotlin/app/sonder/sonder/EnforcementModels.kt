package app.sonder.sonder

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure Kotlin mirror of the Dart EnforcementSnapshot contract.
 *
 * Field names must remain exactly: packageName, surface,
 * grantedUntilEpochMs, lockedUntilEpochMs, lastBackgroundEpochMs.
 * Unknown/malformed snapshots must fail closed for that target.
 */
enum class TargetSurface {
    youtubeShorts,
    instagramReels,
    wholeApp;

    fun toJsonValue(): String = name

    companion object {
        fun fromJsonValue(v: String?): TargetSurface? =
            values().firstOrNull { it.name == v }
    }
}

enum class AccessStatus { allowed, needsChallenge, locked }

data class AccessDecision(
    val status: AccessStatus,
    val remainingMs: Long? = null,
)

fun normalizePackageId(packageName: String?): String? =
    packageName?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

data class EnforcementSnapshot(
    val packageName: String,
    val surface: TargetSurface,
    val grantedUntilEpochMs: Long? = null,
    val lockedUntilEpochMs: Long? = null,
    val lastBackgroundEpochMs: Long? = null,
) {
    fun toJsonObject(): JSONObject = JSONObject().apply {
        put("packageName", packageName)
        put("surface", surface.toJsonValue())
        if (grantedUntilEpochMs != null) put("grantedUntilEpochMs", grantedUntilEpochMs) else put("grantedUntilEpochMs", JSONObject.NULL)
        if (lockedUntilEpochMs != null) put("lockedUntilEpochMs", lockedUntilEpochMs) else put("lockedUntilEpochMs", JSONObject.NULL)
        if (lastBackgroundEpochMs != null) put("lastBackgroundEpochMs", lastBackgroundEpochMs) else put("lastBackgroundEpochMs", JSONObject.NULL)
    }

    companion object {
        fun tryFromJson(json: JSONObject): EnforcementSnapshot? {
            val pkg = normalizePackageId(json.optString("packageName", "")) ?: return null
            val surfaceRaw = json.optString("surface", "")
            val surface = TargetSurface.fromJsonValue(surfaceRaw) ?: return null

            fun optLong(key: String): Long? {
                if (!json.has(key) || json.isNull(key)) return null
                // org.json stores numbers as Int/Long/Double; treat strictly.
                val raw = json.opt(key) ?: return null
                return when (raw) {
                    is Number -> raw.toLong()
                    else -> return null // type violation => malformed
                }
            }

            // Reject non-numeric non-null values: check types explicitly
            for (k in listOf("grantedUntilEpochMs", "lockedUntilEpochMs", "lastBackgroundEpochMs")) {
                if (!json.isNull(k) && json.has(k)) {
                    val v = json.opt(k)
                    if (v != null && v !is Number && v != JSONObject.NULL) return null
                    // JSON strings that look like numbers are rejected
                    if (v is String) return null
                }
            }

            val granted = optLong("grantedUntilEpochMs")
            val locked = optLong("lockedUntilEpochMs")
            val bg = optLong("lastBackgroundEpochMs")
            if (granted != null && granted < 0) return null
            if (locked != null && locked < 0) return null
            if (bg != null && bg < 0) return null
            return EnforcementSnapshot(
                packageName = pkg,
                surface = surface,
                grantedUntilEpochMs = granted,
                lockedUntilEpochMs = locked,
                lastBackgroundEpochMs = bg,
            )
        }

        fun tryFromMap(map: Map<*, *>): EnforcementSnapshot? {
            val pkg = normalizePackageId(map["packageName"] as? String) ?: return null
            val surfaceRaw = map["surface"] as? String ?: return null
            val surface = TargetSurface.fromJsonValue(surfaceRaw) ?: return null
            fun extract(key: String): Long? {
                if (!map.containsKey(key)) return null
                val v = map[key] ?: return null
                return when (v) {
                    is Number -> v.toLong()
                    else -> return null // signal malformed via sentinel handled below
                }
            }
            // Check types: any non-Number non-null value is malformed
            for (k in listOf("grantedUntilEpochMs", "lockedUntilEpochMs", "lastBackgroundEpochMs")) {
                if (map.containsKey(k)) {
                    val v = map[k]
                    if (v != null && v !is Number) return null
                }
            }
            val granted = if (map.containsKey("grantedUntilEpochMs")) extract("grantedUntilEpochMs") else null
            val locked = if (map.containsKey("lockedUntilEpochMs")) extract("lockedUntilEpochMs") else null
            val bg = if (map.containsKey("lastBackgroundEpochMs")) extract("lastBackgroundEpochMs") else null
            // Re-check we did not silently swallow a type error (extract returns null for type errors)
            // but the above guard already returned null for type errors, so okay.
            if (granted != null && granted < 0) return null
            if (locked != null && locked < 0) return null
            if (bg != null && bg < 0) return null
            return EnforcementSnapshot(pkg, surface, granted, locked, bg)
        }
    }
}

// ---------------------------------------------------------------------------
// Policy helpers — exact mirrors of Dart lib/domain/access_policy.dart.
// Keep these pure so they can be unit-tested without Android framework.
// ---------------------------------------------------------------------------

const val GRANT_DURATION_MS: Long = 300_000L // 5m
const val LOCK_DURATION_MS: Long = 600_000L  // 10m
const val ABANDON_THRESHOLD_MS: Long = 20_000L // 20s

fun evaluateAccess(state: EnforcementSnapshot, nowEpochMs: Long): AccessDecision {
    val lockedUntil = state.lockedUntilEpochMs
    if (lockedUntil != null && lockedUntil > nowEpochMs) {
        return AccessDecision(AccessStatus.locked, lockedUntil - nowEpochMs)
    }
    val grantedUntil = state.grantedUntilEpochMs
    if (grantedUntil != null && grantedUntil > nowEpochMs) {
        return AccessDecision(AccessStatus.allowed, grantedUntil - nowEpochMs)
    }
    return AccessDecision(AccessStatus.needsChallenge, null)
}

fun applyWin(state: EnforcementSnapshot, nowEpochMs: Long): EnforcementSnapshot =
    EnforcementSnapshot(
        packageName = state.packageName,
        surface = state.surface,
        grantedUntilEpochMs = nowEpochMs + GRANT_DURATION_MS,
        lockedUntilEpochMs = null,
        lastBackgroundEpochMs = null,
    )

fun applyLoss(state: EnforcementSnapshot, nowEpochMs: Long): EnforcementSnapshot =
    EnforcementSnapshot(
        packageName = state.packageName,
        surface = state.surface,
        grantedUntilEpochMs = null,
        lockedUntilEpochMs = nowEpochMs + LOCK_DURATION_MS,
        lastBackgroundEpochMs = null,
    )

fun recordForeground(state: EnforcementSnapshot, @Suppress("UNUSED_PARAMETER") nowEpochMs: Long): EnforcementSnapshot {
    if (state.lastBackgroundEpochMs == null) return state
    return state.copy(lastBackgroundEpochMs = null)
}

fun recordBackground(state: EnforcementSnapshot, nowEpochMs: Long): EnforcementSnapshot {
    if (state.grantedUntilEpochMs == null) return state
    return state.copy(lastBackgroundEpochMs = nowEpochMs)
}

fun expireAbandonedGrant(state: EnforcementSnapshot, nowEpochMs: Long): EnforcementSnapshot {
    val granted = state.grantedUntilEpochMs ?: return state
    val lastBg = state.lastBackgroundEpochMs ?: return state
    @Suppress("UNUSED_VARIABLE") val unused = granted // keep symmetry with Dart
    if (nowEpochMs - lastBg >= ABANDON_THRESHOLD_MS) {
        return state.copy(grantedUntilEpochMs = null, lastBackgroundEpochMs = null)
    }
    return state
}

// Convenience copy helper
private fun EnforcementSnapshot.copy(
    packageName: String = this.packageName,
    surface: TargetSurface = this.surface,
    grantedUntilEpochMs: Long? = this.grantedUntilEpochMs,
    lockedUntilEpochMs: Long? = this.lockedUntilEpochMs,
    lastBackgroundEpochMs: Long? = this.lastBackgroundEpochMs,
): EnforcementSnapshot = EnforcementSnapshot(
    packageName = packageName,
    surface = surface,
    grantedUntilEpochMs = grantedUntilEpochMs,
    lockedUntilEpochMs = lockedUntilEpochMs,
    lastBackgroundEpochMs = lastBackgroundEpochMs,
)

/**
 * Best-effort Shorts/Reels classifier.
 *
 * Contract: unknown surfaces must NOT be falsely labelled as Shorts/Reels.
 * Only a small set of defensible signals is used; anything else returns null
 * (meaning "treat as regular app foreground" or "do not intercept on surface").
 *
 * The classifier is intentionally conservative and documented as unreliable.
 */
/** Pure package matching/selection used by the accessibility service. */
object ForegroundPackageResolver {
    fun resolve(
        eventPackage: String?,
        rootPackage: String?,
        configuredPackages: Set<String> = emptySet(),
    ): String? {
        val event = normalizePackageId(eventPackage)
        val root = normalizePackageId(rootPackage)
        val configured = configuredPackages.mapNotNull(::normalizePackageId).toSet()
        return when {
            event != null && event in configured -> event
            root != null && root in configured -> root
            event != null -> event
            else -> root
        }
    }

    fun matches(snapshot: EnforcementSnapshot, foregroundPackage: String?): Boolean =
        normalizePackageId(snapshot.packageName) == normalizePackageId(foregroundPackage)
}

fun selectEnforcementSnapshot(
    foregroundPackage: String?,
    snapshots: List<EnforcementSnapshot>,
    classifiedSurface: TargetSurface?,
): EnforcementSnapshot? {
    val packageId = normalizePackageId(foregroundPackage) ?: return null
    val candidates = snapshots.filter { ForegroundPackageResolver.matches(it, packageId) }
    if (candidates.isEmpty()) return null

    // wholeApp is a package-level gate. It must win even when the view classifier
    // reports a different built-in surface or an unrelated signal.
    candidates.firstOrNull { it.surface == TargetSurface.wholeApp }?.let { return it }
    classifiedSurface?.let { surface ->
        candidates.firstOrNull { it.surface == surface }?.let { return it }
    }
    return candidates.firstOrNull {
        it.surface == TargetSurface.youtubeShorts || it.surface == TargetSurface.instagramReels
    }
}

object SurfaceClassifier {

    /**
     * Attempts to classify an in-app surface for [packageName] given optional
     * view-hierarchy signals.
     *
     * @param packageName foreground package
     * @param viewIdResourceNames optional set of viewIdResourceName values visible in the current node tree
     * @param contentDescriptions optional set of contentDescription values visible in the current node tree
     * @return the inferred [TargetSurface], or null if inconclusive.
     *
     * Unknown/empty signals always return null.
     */
    fun classify(
        packageName: String,
        viewIdResourceNames: Set<String> = emptySet(),
        contentDescriptions: Set<String> = emptySet(),
    ): TargetSurface? {
        val signals = (viewIdResourceNames + contentDescriptions)
            .joinToString(" ")
            .lowercase()
        if (packageName == "com.google.android.youtube") {
            // Only strong Shorts signals are accepted. These IDs have been observed in
            // YouTube's Shorts player but may disappear or be renamed at any time.
            // We deliberately do NOT match generic patterns like "reel" or language-
            // dependent strings.
            val shortsIds = setOf(
                "com.google.android.youtube:id/reel_watch_fragment",
                "com.google.android.youtube:id/reel_player",
                "com.google.android.youtube:id/shorts_container",
                "com.google.android.youtube:id/reel_recycler",
            )
            if (viewIdResourceNames.any { it in shortsIds }) {
                return TargetSurface.youtubeShorts
            }
            return null
        }
        if (packageName == "com.instagram.android") {
            val reelsIds = setOf(
                "com.instagram.android:id/clips_viewer_view_pager",
                "com.instagram.android:id/reels_viewer_pager",
                "com.instagram.android:id/video_view",
            )
            // Only when a known Reels container id is present do we claim Reels.
            if (viewIdResourceNames.any { it in reelsIds }) {
                return TargetSurface.instagramReels
            }
            return null
        }
        // Custom apps are always wholeApp when configured; classifier not used.
        return null
    }

    /**
     * Fail-safe string for logging without personal content.
     */
    fun safeLogPackage(pkg: String?): String = pkg ?: "<null>"
}
