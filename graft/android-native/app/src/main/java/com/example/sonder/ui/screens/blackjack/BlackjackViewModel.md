# android-native/app/src/main/java/com/example/sonder/ui/screens/blackjack/BlackjackViewModel.kt

- TableState · class · L22-L34 — data class TableState( val targetPackage: String = "", val phase: Phase = Phase.IDLE, val playerHand: Hand? = null, val dealerUp: Hand? = null, val dealerFull: Hand? = null, // revealed after stand val debtMinutes: Long = 0, val message: String = "", val showResult: Boolean = false, val lastOutcome: HandOutcome? = null, )
- Phase · enum · L33-L33 — enum class Phase
- BlackjackViewModel · class · L36-L167 — @HiltViewModel class BlackjackViewModel @Inject constructor( private val repository: EnforcementRepository, private val expiryScheduler: com.example.sonder.platform.scheduling.GrantExpiryScheduler, ) : ViewModel()
- start · method · L48-L54 — fun start(targetPackage: String)
- deal · method · L56-L77 — fun deal()
- hit · method · L79-L101 — fun hit()
- stand · method · L103-L107 — fun stand()
- standInternal · method · L109-L137 — private fun standInternal()
- settle · method · L139-L153 — private fun settle(outcome: HandOutcome)
- continueAfterResult · method · L155-L166 — fun continueAfterResult()
