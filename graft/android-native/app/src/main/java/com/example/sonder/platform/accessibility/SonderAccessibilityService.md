# android-native/app/src/main/java/com/example/sonder/platform/accessibility/SonderAccessibilityService.kt

- SonderAccessibilityService · class · L15-L76 — @AndroidEntryPoint class SonderAccessibilityService : AccessibilityService()
- onServiceConnected · method · L20-L30 — override fun onServiceConnected()
- onAccessibilityEvent · method · L32-L40 — override fun onAccessibilityEvent(event: AccessibilityEvent?)
- onInterrupt · method · L42-L42 — override fun onInterrupt()
- onDestroy · method · L44-L47 — override fun onDestroy()
- isIgnored · method · L73-L74 — fun isIgnored(pkg: String): Boolean
- AccessibilityGate · class · L79-L92 — object AccessibilityGate
- onServiceConnected · method · L83-L85 — fun onServiceConnected(service: AccessibilityService)
- onServiceDisconnected · method · L87-L89 — fun onServiceDisconnected()
- isLive · method · L91-L91 — fun isLive(): Boolean
