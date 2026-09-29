# android-native/app/src/main/java/com/example/sonder/ui/screens/targets/TargetsListSource.kt

- TAG · variable · L17-L17 — private const val TAG = "TargetsListSource"
- TargetPickUi · class · L20-L25 — data class TargetPickUi( val packageName: String, val label: String, val enabled: Boolean, val icon: Bitmap? = null, )
- TargetsListSource · class · L35-L109 — class TargetsListSource( private val targetDao: TargetDao, private val appsRepository: InstalledAppsRepository, private val scope: CoroutineScope, )
- retry · method · L74-L76 — fun retry()
- labelOf · method · L83-L84 — fun labelOf(packageName: String): String?
- load · method · L86-L108 — private fun load()
