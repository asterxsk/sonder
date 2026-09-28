# android-native/app/src/main/java/com/example/sonder/ui/screens/targets/TargetsViewModel.kt

- TAG · variable · L25-L25 — private const val TAG = "TargetsViewModel"
- TargetPickUi · class · L27-L32 — data class TargetPickUi( val packageName: String, val label: String, val enabled: Boolean, val icon: Bitmap? = null, )
- TargetsViewModel · class · L34-L139 — @HiltViewModel class TargetsViewModel @Inject constructor( private val targetDao: TargetDao, private val appsRepository: InstalledAppsRepository, ) : ViewModel()
- retry · method · L87-L89 — fun retry()
- loadInstalledApps · method · L91-L113 — private fun loadInstalledApps()
- setTab · method · L115-L117 — fun setTab(value: TargetsTab)
- setQuery · method · L119-L121 — fun setQuery(value: String)
- toggle · method · L123-L138 — fun toggle(packageName: String, label: String, enabled: Boolean)
