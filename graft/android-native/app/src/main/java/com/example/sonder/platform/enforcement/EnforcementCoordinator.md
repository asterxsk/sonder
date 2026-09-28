# android-native/app/src/main/java/com/example/sonder/platform/enforcement/EnforcementCoordinator.kt

- EnforcementCoordinator · class · L24-L211 — @Singleton class EnforcementCoordinator @Inject constructor( @ApplicationContext private val context: Context, private val repository: EnforcementRepository, )
- onWindowEvent · method · L60-L97 — fun onWindowEvent(pkg: String, service: AccessibilityService)
- onGateDismissed · method · L111-L114 — fun onGateDismissed(pkg: String?)
- onGateHandoffFailed · method · L122-L138 — fun onGateHandoffFailed(pkg: String?)
- launchGate · method · L140-L156 — fun launchGate(pkg: String)
- showOverlay · method · L158-L182 — @Synchronized private fun showOverlay( pkg: String, lockoutRemainingMillis: Long, lockoutReason: String?, winGrantMillis: Long, )
- onGateShown · method · L185-L187 — fun onGateShown()
- dismissOverlayIfFor · method · L189-L192 — @Synchronized fun dismissOverlayIfFor(pkg: String)
- isOverlayShownFor · method · L194-L195 — @Synchronized private fun isOverlayShownFor(pkg: String): Boolean
- dismissOverlay · method · L197-L202 — @Synchronized fun dismissOverlay()
