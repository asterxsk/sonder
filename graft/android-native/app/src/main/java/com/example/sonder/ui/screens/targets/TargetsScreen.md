# android-native/app/src/main/java/com/example/sonder/ui/screens/targets/TargetsScreen.kt

- TargetsScreen · function · L55-L224 — @Composable fun TargetsScreen( contentPadding: PaddingValues, onOpenAppSettings: (String) -> Unit, onAddApps: () -> Unit, viewModel: TargetsViewModel = hiltViewModel(), )
- rowPending · function · L232-L250 — private fun rowPending( action: PendingTargetAction, appName: String, onRequest: (String, TargetActionKind) -> Unit, ): TargetRowPending
- AddAppsButton · function · L256-L264 — @Composable private fun AddAppsButton(onClick: () -> Unit, modifier: Modifier = Modifier)
- FramedNote · function · L271-L307 — @Composable internal fun FramedNote( title: String, body: String, busy: Boolean = false, action: (@Composable () -> Unit)? = null, )
