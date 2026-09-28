# android-native/app/src/main/java/com/example/sonder/ui/screens/appsettings/AppSettingsViewModel.kt

- AppSettingsUiState · class · L30-L42 — data class AppSettingsUiState( val packageName: String = "", /** null until the launcher enumeration resolves it; the header falls back to the id. */ val label: String? = null, val rules: AccessRules = AccessRules(), /** * The raw stored overrides behind [rules]. The screen needs these — not the effective * values — to know which knobs are chosen and which inherit, since an effective value * equal to the default is ambiguous. */ val overrides: EnforcementRepository.TargetOverrides = EnforcementRepository.TargetOverrides(), val grantedTodayMillis: Long = 0L, )
- AppSettingsViewModel · class · L44-L132 — @OptIn(ExperimentalCoroutinesApi::class) @HiltViewModel class AppSettingsViewModel @Inject constructor( private val appsRepository: InstalledAppsRepository, private val enforcement: EnforcementRepository, ) : ViewModel()
- bind · method · L94-L111 — fun bind(packageName: String)
- setWinGrant · method · L113-L113 — fun setWinGrant(millis: Long)
- setLossDebt · method · L114-L114 — fun setLossDebt(millis: Long)
- setDebtCeiling · method · L115-L115 — fun setDebtCeiling(millis: Long)
- setAbsenceRevoke · method · L116-L116 — fun setAbsenceRevoke(millis: Long)
- setDailyCap · method · L117-L117 — fun setDailyCap(millis: Long?)
- update · method · L121-L131 — private fun update( transform: (EnforcementRepository.TargetOverrides) -> EnforcementRepository.TargetOverrides, )
