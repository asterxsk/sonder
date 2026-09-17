# android-native/app/src/main/java/com/example/sonder/data/repo/InstalledAppsRepository.kt

- InstalledApp · class · L13-L16 — data class InstalledApp( val packageName: String, val label: String, )
- InstalledAppsRepository · class · L27-L51 — @Singleton class InstalledAppsRepository @Inject constructor( @ApplicationContext private val context: Context, )
- launchableApps · method · L31-L50 — suspend fun launchableApps(): List<InstalledApp>
