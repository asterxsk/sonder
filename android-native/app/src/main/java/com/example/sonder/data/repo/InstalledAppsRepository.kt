package com.example.sonder.data.repo

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledApp(
    val packageName: String,
    val label: String,
)

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
                )
            }
            .filter { it.packageName != context.packageName } // never gate Sonder itself
            .sortedBy { it.label.lowercase() }
            .distinctBy { it.packageName }
    }
}
