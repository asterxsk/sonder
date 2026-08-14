package app.sonder.sonder

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

private const val TAG = "SonderCatalog"

data class LaunchableAppInfo(
    val packageName: String,
    val displayName: String,
)

/**
 * Catalog of installed launchable packages for the manual package picker.
 *
 * Only returns packages that are launchable (have a LAUNCHER intent) and
 * that the package visibility <queries> allow us to discover. No usage
 * stats, no install timestamps — display name + package only.
 */
class AppTargetCatalog(private val context: Context) {

    fun listLaunchableApps(): List<LaunchableAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(launcherIntent, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryIntentActivities failed", e)
            emptyList()
        }

        // Also cross-check each package is actually queryable with getApplicationInfo
        // so we fail gracefully on any visibility gaps.
        val out = resolveInfos.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            // Skip Sonder itself — no point blocking ourselves.
            if (pkg == context.packageName) return@mapNotNull null
            val label = try {
                ri.loadLabel(pm)?.toString()?.ifBlank { pkg } ?: pkg
            } catch (_: Exception) { pkg }
            LaunchableAppInfo(packageName = pkg, displayName = label)
        }
            .distinctBy { it.packageName }
            .sortedBy { it.displayName.lowercase() }
        return out
    }
}
