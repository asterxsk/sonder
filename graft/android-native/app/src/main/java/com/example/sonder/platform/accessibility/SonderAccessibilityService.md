# android-native/app/src/main/java/com/example/sonder/platform/accessibility/SonderAccessibilityService.kt

- SonderAccessibilityService · class · L31-L105 — @AndroidEntryPoint class SonderAccessibilityService : AccessibilityService()
- onReceive · method · L40-L42 — override fun onReceive(context: Context?, intent: Intent?)
- onServiceConnected · method · L45-L55 — override fun onServiceConnected()
- onAccessibilityEvent · method · L57-L65 — override fun onAccessibilityEvent(event: AccessibilityEvent?)
- onInterrupt · method · L67-L67 — override fun onInterrupt()
- onUnbind · method · L69-L75 — override fun onUnbind(intent: Intent?): Boolean
- onDestroy · method · L77-L82 — override fun onDestroy()
- registerScreenOffReceiver · method · L84-L93 — private fun registerScreenOffReceiver()
- unregisterScreenOffReceiver · method · L95-L99 — private fun unregisterScreenOffReceiver()
- AccessibilityGate · class · L108-L121 — object AccessibilityGate
- onServiceConnected · method · L112-L114 — fun onServiceConnected(service: AccessibilityService)
- onServiceDisconnected · method · L116-L118 — fun onServiceDisconnected()
- isLive · method · L120-L120 — fun isLive(): Boolean
