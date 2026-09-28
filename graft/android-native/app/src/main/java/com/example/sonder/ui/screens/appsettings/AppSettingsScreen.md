# android-native/app/src/main/java/com/example/sonder/ui/screens/appsettings/AppSettingsScreen.kt

- AppSettingsScreen · function · L39-L128 — @Composable fun AppSettingsScreen( packageName: String, contentPadding: PaddingValues, onBack: () -> Unit, viewModel: AppSettingsViewModel = hiltViewModel(key = packageName), )
- Knob · function · L137-L169 — @Composable private fun Knob( title: String, presets: List<Preset>, current: Long?, custom: Boolean, onSelect: (Long?) -> Unit, )
- TodayLine · function · L172-L191 — @Composable private fun TodayLine(ui: AppSettingsUiState)
- Preset · class · L194-L194 — private data class Preset(val label: String, val value: Long?)
- WinGrantPresets · variable · L196-L202 — private val WinGrantPresets = listOf( Preset("2:00", 2 * 60_000L), Preset("5:00", 5 * 60_000L), Preset("10:00", 10 * 60_000L), Preset("15:00", 15 * 60_000L), Preset("30:00", 30 * 60_000L), )
- LossPenaltyPresets · variable · L203-L208 — private val LossPenaltyPresets = listOf( Preset("5:00", 5 * 60_000L), Preset("10:00", 10 * 60_000L), Preset("20:00", 20 * 60_000L), Preset("30:00", 30 * 60_000L), )
- DebtCeilingPresets · variable · L209-L213 — private val DebtCeilingPresets = listOf( Preset("30:00", 30 * 60_000L), Preset("1:00", 60 * 60_000L), Preset("2:00", 2 * 60 * 60_000L), )
- AbsenceRevokePresets · variable · L214-L219 — private val AbsenceRevokePresets = listOf( Preset("0:30", 30_000L), Preset("1:00", 60_000L), Preset("2:00", 2 * 60_000L), Preset("5:00", 5 * 60_000L), )
- DailyCapPresets · variable · L220-L226 — private val DailyCapPresets = listOf( Preset("OFF", null), Preset("30:00", 30 * 60_000L), Preset("1:00", 60 * 60_000L), Preset("2:00", 2 * 60 * 60_000L), Preset("3:00", 3 * 60 * 60_000L), )
- formatDuration · function · L229-L239 — private fun formatDuration(millis: Long): String
