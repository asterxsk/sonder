# android-native/app/src/main/java/com/example/sonder/platform/permissions/PermissionAudit.kt

- SonderPermission · enum · L16-L21 — enum class SonderPermission(val label: String)
- PermissionAudit · class · L31-L91 — @Singleton class PermissionAudit @Inject constructor( @ApplicationContext private val context: Context, )
- missingPermissions · method · L36-L37 — fun missingPermissions(): Set<SonderPermission>
- isGranted · method · L43-L48 — fun isGranted(permission: SonderPermission): Boolean
- isAccessibilityEnabled · method · L50-L58 — fun isAccessibilityEnabled(): Boolean
- isUsageAccessGranted · method · L60-L77 — fun isUsageAccessGranted(): Boolean
- isNotificationPermissionGranted · method · L79-L85 — fun isNotificationPermissionGranted(): Boolean
