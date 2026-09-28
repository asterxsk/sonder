# android-native/app/src/main/java/com/example/sonder/ui/screens/blackjack/BlackjackViewModel.kt

- TableState · class · L23-L48 — data class TableState( val targetPackage: String = "", val phase: Phase = Phase.IDLE, val playerHand: Hand? = null, val dealerUp: Hand? = null, val dealerFull: Hand? = null, // revealed after stand val debtMinutes: Long = 0, val message: String = "", val showResult: Boolean = false, val lastOutcome: HandOutcome? = null, /** Effective win grant for this target: the per-app override, else the global default. */ val winGrantMillis: Long = AccessPolicy.WIN_GRANT_MILLIS, /** Remaining allowance under the app's daily cap; null when the app has no cap. */ val dailyRemainingMillis: Long? = null, /** * Whether the last settled hand actually granted access. A debt-free win grants * nothing once the daily cap is spent, so the RESOLVED success path must key off * this rather than off "won with no debt", or the gate would report CONTINUE with * no grant behind it and the coordinator would re-raise the gate. */ val lastHandGranted: Boolean = false, /** True while the app is under an active DAILY_CAP lockout; the cap is not time-served. */ val capLocked: Boolean = false, )
- Phase · enum · L47-L47 — enum class Phase
- BlackjackViewModel · class · L50-L209 — @HiltViewModel class BlackjackViewModel @Inject constructor( private val repository: EnforcementRepository, private val expiryScheduler: com.example.sonder.platform.scheduling.GrantExpiryScheduler, ) : ViewModel()
- start · method · L62-L68 — fun start(targetPackage: String)
- refreshPolicy · method · L75-L90 — private suspend fun refreshPolicy()
- deal · method · L92-L115 — fun deal()
- hit · method · L117-L139 — fun hit()
- stand · method · L141-L145 — fun stand()
- standInternal · method · L147-L175 — private fun standInternal()
- settle · method · L177-L194 — private fun settle(outcome: HandOutcome)
- continueAfterResult · method · L196-L208 — fun continueAfterResult()
