# android-native/app/src/main/java/com/example/sonder/platform/permissions/PermissionAudit.kt

- SonderPermission · enum · L16-L21 — enum class SonderPermission(val label: String)
- PermissionAudit · class · L29-L106 — @Singleton class PermissionAudit @Inject constructor( @ApplicationContext private val context: Context, )
- missingPermissions · method · L34-L41 — fun missingPermissions(): Set<SonderPermission>
- isAccessibilityEnabled · method · L43-L51 — fun isAccessibilityEnabled(): Boolean
- isUsageAccessGranted · method · L53-L70 — fun isUsageAccessGranted(): Boolean
- isNotificationPermissionGranted · method · L72-L78 — fun isNotificationPermissionGranted(): Boolean
- scheduleOnOpenAudit · method · L87-L104 — fun scheduleOnOpenAudit(context: Context)
