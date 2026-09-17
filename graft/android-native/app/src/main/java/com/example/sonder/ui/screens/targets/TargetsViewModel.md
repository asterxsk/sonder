# android-native/app/src/main/java/com/example/sonder/ui/screens/targets/TargetsViewModel.kt

- TAG · variable · L24-L24 — private const val TAG = "TargetsViewModel"
- TargetPickUi · class · L26-L30 — data class TargetPickUi( val packageName: String, val label: String, val enabled: Boolean, )
- TargetsViewModel · class · L32-L127 — @HiltViewModel class TargetsViewModel @Inject constructor( private val targetDao: TargetDao, private val appsRepository: InstalledAppsRepository, ) : ViewModel()
- retry · method · L79-L81 — fun retry()
- loadInstalledApps · method · L83-L105 — private fun loadInstalledApps()
- setTab · method · L107-L109 — fun setTab(value: TargetsTab)
- setQuery · method · L111-L113 — fun setQuery(value: String)
- toggle · method · L115-L126 — fun toggle(packageName: String, label: String, enabled: Boolean)
