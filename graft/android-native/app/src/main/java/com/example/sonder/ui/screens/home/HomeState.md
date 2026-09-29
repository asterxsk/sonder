# android-native/app/src/main/java/com/example/sonder/ui/screens/home/HomeState.kt

- HomeLockout · enum · L19-L19 — enum class HomeLockout
- HomeBadge · enum · L26-L26 — enum class HomeBadge
- HomeRow · class · L29-L39 — data class HomeRow( val packageName: String, val label: String, val state: EnforcementState, /** `MM:SS` while a grant or a debt lockout is running; empty when nothing counts down. */ val remainingText: String, /** The lockout kind when [state] is LOCKED; null otherwise. */ val lockout: HomeLockout? = null, /** Local wall-clock `HH:mm` a DAILY_CAP row resets at; empty for every other row. */ val resetText: String = "", )
- badge · function · L45-L46 — internal fun HomeRow.badge(): HomeBadge
- badgeText · function · L53-L56 — internal fun HomeRow.badgeText(): String?
- stateWord · function · L62-L68 — internal fun HomeRow.stateWord(): String
- HomeSummary · interface · L71-L80 — sealed interface HomeSummary
- NoTargets · class · L73-L73 — data object NoTargets : HomeSummary
- Idle · class · L76-L76 — data class Idle(val enabledCount: Int) : HomeSummary
- Active · class · L79-L79 — data class Active(val row: HomeRow) : HomeSummary
- HomeState · class · L86-L89 — data class HomeState( val summary: HomeSummary, val rows: List<HomeRow>, )
- mapHomeState · function · L97-L159 — internal fun mapHomeState( targets: List<TargetEntity>, grants: List<GrantSnapshot>, lockouts: List<LockoutSnapshot>, nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault(), ): HomeState
- urgency · function · L166-L172 — private fun urgency(row: HomeRow): Int
- lockoutKind · function · L175-L177 — private fun lockoutKind(reason: String?): HomeLockout
- formatRemaining · function · L180-L183 — internal fun formatRemaining(millis: Long): String
- formatResetClock · function · L186-L187 — internal fun formatResetClock(millis: Long, zoneId: ZoneId): String
- ResetClock · variable · L189-L189 — private val ResetClock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
