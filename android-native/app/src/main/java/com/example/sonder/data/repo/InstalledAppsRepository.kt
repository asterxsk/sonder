package com.example.sonder.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
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

/**
 * Lists launchable user apps for the Targets picker.
 * Works with the manifest <queries> MAIN/LAUNCHER declaration (API 30+ visibility),
 * no QUERY_ALL_PACKAGES needed.
 *
 * PackageManager is binder traffic, so the query always runs on [Dispatchers.IO].
 * Nothing is cached, here or process-wide: a fresh call sees apps installed or
 * removed since the last one.
 */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
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
                    // One package's art must not fail the whole enumeration, so the
                    // load is contained; a missing icon just leaves the row's glyph.
                    icon = runCatching {
                        ri.loadIcon(pm)?.toBitmap(IconEdgePx, IconEdgePx)
                    }.getOrNull(),
                )
            }
            .filter { it.packageName != context.packageName } // never gate Sonder itself
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.packageName }
    }
}
