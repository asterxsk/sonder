# android-native/app/src/main/java/com/example/sonder/ui/screens/home/HomeScreen.kt

- HomeScreen · function · L49-L108 — @Composable fun HomeScreen( contentPadding: PaddingValues, onSelectTab: (PixelTab) -> Unit, viewModel: HomeViewModel = hiltViewModel(), )
- HomeLoadingPanel · function · L111-L146 — @Composable private fun HomeLoadingPanel()
- HomeStatusPanel · function · L149-L215 — @Composable private fun HomeStatusPanel(summary: HomeSummary, onLimitApps: () -> Unit)
- HomeTargetPanel · function · L218-L261 — @Composable private fun HomeTargetPanel(row: HomeRow)
- limitedCountLabel · function · L264-L265 — private fun limitedCountLabel(count: Int): String
- stateWord · function · L268-L273 — private fun HomeRow.stateWord(): String
