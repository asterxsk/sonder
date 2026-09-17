# android-native/app/src/main/java/com/example/sonder/ui/screens/home/HomeState.kt

- HomeRow · class · L11-L17 — data class HomeRow( val packageName: String, val label: String, val state: EnforcementState, /** `MM:SS` while a grant or lockout is running; empty when nothing counts down. */ val remainingText: String, )
- HomeSummary · interface · L20-L29 — sealed interface HomeSummary
- NoTargets · class · L22-L22 — data object NoTargets : HomeSummary
- Idle · class · L25-L25 — data class Idle(val enabledCount: Int) : HomeSummary
- Active · class · L28-L28 — data class Active(val row: HomeRow) : HomeSummary
- HomeState · class · L35-L38 — data class HomeState( val summary: HomeSummary, val rows: List<HomeRow>, )
- mapHomeState · function · L46-L94 — internal fun mapHomeState( targets: List<TargetEntity>, grants: List<GrantSnapshot>, lockouts: List<LockoutSnapshot>, nowMillis: Long, ): HomeState
- urgency · function · L97-L102 — private fun urgency(state: EnforcementState): Int
- formatRemaining · function · L105-L108 — internal fun formatRemaining(millis: Long): String
