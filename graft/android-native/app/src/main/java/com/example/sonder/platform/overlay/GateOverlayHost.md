# android-native/app/src/main/java/com/example/sonder/platform/overlay/GateOverlayHost.kt

- GateOverlayHost · class · L46-L204 — @Singleton class GateOverlayHost @Inject constructor( @ApplicationContext private val context: Context, private val controller: GateController, )
- showGate · method · L67-L67 — fun showGate(pkg: String, label: String)
- showLockout · method · L69-L70 — fun showLockout(pkg: String, label: String, remainingMillis: Long)
- show · method · L72-L98 — private fun show(pkg: String, label: String, lockoutRemainingMillis: Long)
- dismiss · method · L101-L103 — fun dismiss(reason: String = "unspecified")
- removeOverlay · method · L105-L119 — private fun removeOverlay(reason: String)
- attachViewTreeOwners · method · L128-L132 — private fun attachViewTreeOwners(view: View, owner: OverlayLifecycleOwner)
- setOwnerTag · method · L134-L141 — private fun setOwnerTag(view: View, idName: String, owner: Any)
- teardownOwner · method · L143-L148 — private fun teardownOwner()
- buildView · method · L150-L184 — private fun buildView(pkg: String, label: String, lockoutRemainingMillis: Long): View
- layoutParams · method · L186-L199 — private fun layoutParams()
