# android-native/app/src/main/java/com/example/sonder/data/repo/InstalledAppsRepository.kt

- InstalledApp · class · L15-L19 — data class InstalledApp( val packageName: String, val label: String, val icon: Bitmap? = null, )
- IconEdgePx · variable · L25-L25 — private const val IconEdgePx = 96
- InstalledAppsRepository · class · L36-L78 — @Singleton class InstalledAppsRepository @Inject constructor( @ApplicationContext private val context: Context, )
- labelFor · method · L46-L51 — suspend fun labelFor(packageName: String): String?
- launchableApps · method · L53-L77 — suspend fun launchableApps(): List<InstalledApp>
