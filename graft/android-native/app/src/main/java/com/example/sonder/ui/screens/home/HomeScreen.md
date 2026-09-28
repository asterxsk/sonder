# android-native/app/src/main/java/com/example/sonder/ui/screens/home/HomeScreen.kt

- HomeScreen · function · L51-L121 — @Composable fun HomeScreen( contentPadding: PaddingValues, onSelectTab: (PixelTab) -> Unit, viewModel: HomeViewModel = hiltViewModel(), )
- HomeLoadingPanel · function · L124-L141 — @Composable private fun HomeLoadingPanel()
- HomeStatusPanel · function · L144-L220 — @Composable private fun HomeStatusPanel(summary: HomeSummary, onLimitApps: () -> Unit)
- HomeTargetPanel · function · L223-L272 — @Composable private fun HomeTargetPanel(row: HomeRow)
- CursorBlockSize · variable · L275-L275 — private val CursorBlockSize = 8.dp
- limitedCountLabel · function · L278-L279 — private fun limitedCountLabel(count: Int): String
- HomeResetFrame · function · L285-L310 — @Composable private fun HomeResetFrame(resetText: String)
- stateWord · function · L313-L319 — private fun HomeRow.stateWord(): String
