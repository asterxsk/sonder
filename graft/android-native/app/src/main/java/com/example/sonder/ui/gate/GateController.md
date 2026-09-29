# android-native/app/src/main/java/com/example/sonder/ui/gate/GateController.kt

- TableState · class · L23-L48 — data class TableState( val targetPackage: String = "", val phase: Phase = Phase.IDLE, val playerHand: Hand? = null, val dealerUp: Hand? = null, val dealerFull: Hand? = null, // revealed after stand val debtMinutes: Long = 0, val message: String = "", val showResult: Boolean = false, val lastOutcome: HandOutcome? = null, /** Effective win grant for this target: the per-app override, else the global default. */ val winGrantMillis: Long = AccessPolicy.WIN_GRANT_MILLIS, /** Remaining allowance under the app's daily cap; null when the app has no cap. */ val dailyRemainingMillis: Long? = null, /** * Whether the last settled hand actually granted access. A debt-free win grants * nothing once the daily cap is spent, so the RESOLVED success path must key off * this rather than off "won with no debt", or the gate would report CONTINUE with * no grant behind it and the coordinator would re-raise the gate. */ val lastHandGranted: Boolean = false, /** True while the app is under an active DAILY_CAP lockout; the cap is not time-served. */ val capLocked: Boolean = false, )
- Phase · enum · L47-L47 — enum class Phase
- GateController · class · L56-L236 — @Singleton class GateController @Inject constructor( private val repository: EnforcementRepository, private val expiryScheduler: GrantExpiryScheduler, @ApplicationScope private val scope: CoroutineScope, )
- begin · method · L73-L87 — fun begin(targetPackage: String)
- refreshPolicy · method · L94-L109 — private suspend fun refreshPolicy()
- deal · method · L111-L129 — fun deal()
- hit · method · L131-L153 — fun hit()
- stand · method · L155-L159 — fun stand()
- playAgain · method · L161-L173 — fun playAgain()
- releaseAccess · method · L176-L183 — fun releaseAccess()
- standInternal · method · L185-L213 — private fun standInternal()
- settle · method · L215-L235 — private fun settle(outcome: HandOutcome)
