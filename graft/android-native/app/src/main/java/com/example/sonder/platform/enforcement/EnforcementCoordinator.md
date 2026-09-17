# android-native/app/src/main/java/com/example/sonder/platform/enforcement/EnforcementCoordinator.kt

- EnforcementCoordinator · class · L23-L142 — @Singleton class EnforcementCoordinator @Inject constructor( @ApplicationContext private val context: Context, private val repository: EnforcementRepository, )
- onWindowEvent · method · L47-L83 — fun onWindowEvent(pkg: String, service: AccessibilityService)
- onGateDismissed · method · L86-L90 — fun onGateDismissed(pkg: String?)
- launchGate · method · L92-L108 — fun launchGate(pkg: String)
- showOverlay · method · L110-L120 — private fun showOverlay(pkg: String, lockoutRemainingMillis: Long)
- onGateShown · method · L123-L125 — fun onGateShown()
- dismissOverlayIfFor · method · L127-L129 — fun dismissOverlayIfFor(pkg: String)
- dismissOverlay · method · L131-L136 — @Synchronized fun dismissOverlay()
