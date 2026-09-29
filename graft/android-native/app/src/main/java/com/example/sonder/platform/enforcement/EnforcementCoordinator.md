# android-native/app/src/main/java/com/example/sonder/platform/enforcement/EnforcementCoordinator.kt

- EnforcementCoordinator · class · L44-L189 — @Singleton class EnforcementCoordinator @Inject constructor( @ApplicationContext private val context: Context, private val repository: EnforcementRepository, private val overlayHost: GateOverlayHost, private val foregroundResolver: ForegroundResolver, )
- onForeground · method · L57-L77 — fun onForeground(pkg: String, surface: ForegroundSurface, className: String? = null)
- evaluate · method · L83-L145 — private suspend fun evaluate( pkg: String, surface: ForegroundSurface, className: String?, cancelVerification: Boolean, )
- scheduleForegroundVerification · method · L152-L167 — private fun scheduleForegroundVerification()
- onScreenOff · method · L170-L174 — fun onScreenOff()
- onServiceStopped · method · L177-L181 — fun onServiceStopped()
