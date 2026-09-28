# android-native/app/src/main/java/com/example/sonder/ui/screens/home/HomeState.kt

- HomeLockout · enum · L19-L19 — enum class HomeLockout
- HomeRow · class · L22-L32 — data class HomeRow( val packageName: String, val label: String, val state: EnforcementState, /** `MM:SS` while a grant or a debt lockout is running; empty when nothing counts down. */ val remainingText: String, /** The lockout kind when [state] is LOCKED; null otherwise. */ val lockout: HomeLockout? = null, /** Local wall-clock `HH:mm` a DAILY_CAP row resets at; empty for every other row. */ val resetText: String = "", )
- HomeSummary · interface · L35-L44 — sealed interface HomeSummary
- NoTargets · class · L37-L37 — data object NoTargets : HomeSummary
- Idle · class · L40-L40 — data class Idle(val enabledCount: Int) : HomeSummary
- Active · class · L43-L43 — data class Active(val row: HomeRow) : HomeSummary
- HomeState · class · L50-L53 — data class HomeState( val summary: HomeSummary, val rows: List<HomeRow>, )
- mapHomeState · function · L61-L123 — internal fun mapHomeState( targets: List<TargetEntity>, grants: List<GrantSnapshot>, lockouts: List<LockoutSnapshot>, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault(), ): HomeState
- urgency · function · L130-L136 — private fun urgency(row: HomeRow): Int
- lockoutKind · function · L139-L141 — private fun lockoutKind(reason: String?): HomeLockout
- formatRemaining · function · L144-L147 — internal fun formatRemaining(millis: Long): String
- formatResetClock · function · L150-L151 — internal fun formatResetClock(millis: Long, zoneId: ZoneId): String
- ResetClock · variable · L153-L153 — private val ResetClock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
