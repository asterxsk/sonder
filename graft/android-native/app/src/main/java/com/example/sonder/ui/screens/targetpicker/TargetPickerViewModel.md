# android-native/app/src/main/java/com/example/sonder/ui/screens/targetpicker/TargetPickerViewModel.kt

- TargetPickerViewModel · class · L26-L107 — @HiltViewModel class TargetPickerViewModel @Inject constructor( private val targetDao: TargetDao, appsRepository: InstalledAppsRepository, ) : ViewModel()
- retry · method · L54-L56 — fun retry()
- reset · method · L64-L67 — fun reset()
- setQuery · method · L69-L71 — fun setQuery(value: String)
- toggleSelection · method · L74-L77 — fun toggleSelection(packageName: String)
- commit · method · L90-L106 — fun commit(onCommitted: () -> Unit)
