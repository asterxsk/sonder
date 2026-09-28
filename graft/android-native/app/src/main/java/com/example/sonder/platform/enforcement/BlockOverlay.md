# android-native/app/src/main/java/com/example/sonder/platform/enforcement/BlockOverlay.kt

- BlockOverlay · class · L22-L165 — class BlockOverlay( private val context: Context, private val targetLabel: String, private val lockoutRemainingMillis: Long, /** Effective win grant for this target, so the un-locked copy names the real amount. */ private val winGrantMillis: Long, /** Lockout cause ("DEBT" | "DAILY_CAP" | null); a cap lockout must not read as debt. */ private val lockoutReason: String?, private val onTap: () -> Unit, private val onAutoDismiss: () -> Unit = {}, )
- show · method · L51-L72 — fun show(): Boolean
- dismiss · method · L74-L82 — fun dismiss()
- buildView · method · L92-L164 — private fun buildView(): View
- formatAccess · function · L168-L171 — private fun formatAccess(millis: Long): String
