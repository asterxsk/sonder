package com.example.sonder.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Bitmap? = null,
)

/**
 * Launcher art is square; rasterize every icon to this edge so a list of ~150 apps
 * holds small bitmaps instead of the full-size drawables the framework hands back.
 */
private const val IconEdgePx = 96

/** How much icon art to keep resident: about a hundred apps at the edge above. */
private const val ICON_CACHE_BYTES = 4 * 1024 * 1024

/**
 * Lists launchable user apps for the Targets picker.
 * Works with the manifest <queries> MAIN/LAUNCHER declaration (API 30+ visibility),
 * no QUERY_ALL_PACKAGES needed.
 *
 * PackageManager is binder traffic, so the query always runs on [Dispatchers.IO]. The
 * *list* is never cached — a fresh call sees apps installed or removed since the last one —
 * but the icons are: rasterizing ~150 launcher drawables costs real time and ~300 KB each
 * at the source size, and the picker is reopened often. Icons change only on an app update,
 * which is rare enough that showing the previous one until the process restarts is a better
 * trade than re-rasterizing the set on every open.
 */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Bounded by bytes rather than by entry count: launcher art is not all one size, and a
     * count-based bound on 150 apps either wastes memory or evicts constantly.
     * `LruCache` is not thread-safe, so every access is synchronized.
     */
    private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    /**
     * The launcher label for a single package, or null when it is not installed or not
     * visible to us. Reads the label straight off the application info and touches no
     * drawables, so a caller that only needs one name does not pay for [launchableApps]'s
     * per-app bitmap rasterization.
     */
    suspend fun labelFor(packageName: String): String? = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        }.getOrNull()
    }

    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        resolveInfos
            .map { ri ->
                InstalledApp(
                    packageName = ri.activityInfo.packageName,
                    label = ri.loadLabel(pm).toString(),
                    icon = iconFor(pm, ri),
                )
            }
            .filter { it.packageName != context.packageName } // never gate Sonder itself
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.packageName }
    }

    /**
     * The rasterized icon for one entry, from the cache when it is there.
     *
     * One package's art must not fail the whole enumeration, so the load is contained; a
     * missing icon just leaves the row's glyph, and a failed load is not cached, so the
     * next open tries again.
     */
    private fun iconFor(pm: PackageManager, ri: ResolveInfo): Bitmap? {
        val pkg = ri.activityInfo.packageName
        synchronized(iconCache) { iconCache.get(pkg) }?.let { return it }
        val loaded = runCatching {
            ri.loadIcon(pm)?.toBitmap(IconEdgePx, IconEdgePx)
        }.getOrNull() ?: return null
        synchronized(iconCache) { iconCache.put(pkg, loaded) }
        return loaded
    }
}
