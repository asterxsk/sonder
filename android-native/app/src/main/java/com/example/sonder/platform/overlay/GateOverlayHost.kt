package com.example.sonder.platform.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import com.example.sonder.BuildConfig
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.SonderTheme
import com.example.sonder.ui.gate.GateContent
import com.example.sonder.ui.gate.GateController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The blocker: a single `TYPE_APPLICATION_OVERLAY` window owned by the
 * accessibility service — never by an Activity.
 *
 * Because it is a window and not an Activity it is not part of any task, so it
 * cannot show up as "a screen in the detox app", cannot be reached from Recents,
 * and is unaffected by the detox app's navigation. The window is opaque and
 * touchable, so it swallows touches before they reach the blocked app below.
 *
 * Compose inside the window is hosted the way AndroidX hosts it outside an
 * Activity: the blocker supplies its own view-tree owners (see
 * [OverlayLifecycleOwner]), so nothing depends on the detox app's UI stack.
 *
 * Exactly one overlay exists at a time: showing for the package already blocked
 * is a no-op, so rapid window events cannot stack blockers or reset a live hand.
 */
@Singleton
class GateOverlayHost @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: GateController,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    /** Drives recomposition for the overlay only; cancelled with the overlay. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var root: View? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var unlockWatcher: Job? = null

    /** Package the blocker is currently covering, or null when it is down. */
    @Volatile
    var shownForPackage: String? = null
        private set

    fun showGate(pkg: String, label: String) = show(pkg, label, lockoutRemainingMillis = 0L)

    fun showLockout(pkg: String, label: String, remainingMillis: Long) =
        show(pkg, label, remainingMillis)

    private fun show(pkg: String, label: String, lockoutRemainingMillis: Long) {
        mainHandler.post {
            // Idempotent: never stack overlays, and never reset a hand in progress.
            if (shownForPackage == pkg && root != null) return@post

            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "blocker not shown for $pkg: overlay permission missing")
                return@post
            }

            removeOverlay(reason = "replacing")
            controller.begin(pkg)

            try {
                val view = buildView(pkg, label, lockoutRemainingMillis)
                windowManager.addView(view, layoutParams())
                root = view
                shownForPackage = pkg
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "blocker up for $pkg lockoutMs=$lockoutRemainingMillis")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "blocker add failed for $pkg", t)
                teardownOwner()
            }
        }
    }

    /** Take the blocker down. Safe to call when nothing is showing. */
    fun dismiss(reason: String = "unspecified") {
        mainHandler.post { removeOverlay(reason) }
    }

    private fun removeOverlay(reason: String) {
        if (root != null && BuildConfig.DEBUG) {
            Log.d(TAG, "blocker down for $shownForPackage ($reason)")
        }
        root?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (_: Throwable) {
                // Already detached by the system (config change, window token loss).
            }
        }
        root = null
        shownForPackage = null
        teardownOwner()
    }

    /**
     * Compose refuses to compose into a view that doesn't propagate a
     * ViewTreeLifecycleOwner. AndroidX exposes the setters only as internal
     * plumbing in current releases (the JVM methods remain but are hidden from
     * Kotlin), so the owners are attached exactly as the libraries read them:
     * via the view tag whose id those libraries publish as a resource.
     */
    private fun attachViewTreeOwners(view: View, owner: OverlayLifecycleOwner) {
        setOwnerTag(view, "view_tree_lifecycle_owner", owner)
        setOwnerTag(view, "view_tree_view_model_store_owner", owner)
        setOwnerTag(view, "view_tree_saved_state_registry_owner", owner)
    }

    private fun setOwnerTag(view: View, idName: String, owner: Any) {
        val id = view.resources.getIdentifier(idName, "id", view.context.packageName)
        if (id == 0) {
            Log.w(TAG, "view-tree owner id $idName not found; Compose host may reject the window")
            return
        }
        view.setTag(id, owner)
    }

    private fun teardownOwner() {
        unlockWatcher?.cancel()
        unlockWatcher = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    private fun buildView(pkg: String, label: String, lockoutRemainingMillis: Long): View {
        // Compose without an Activity: the blocker owns the view-tree owners.
        val owner = OverlayLifecycleOwner().also { it.start() }
        lifecycleOwner = owner

        val composeView = ComposeView(context).apply {
            attachViewTreeOwners(this, owner)
            // Opaque background: no frame of the blocked app may show through.
            setBackgroundColor(PixelPalette.Bg.toArgb())
            setContent {
                val state by controller.state.collectAsState()
                SonderTheme {
                    GateContent(
                        label = label,
                        state = state,
                        lockoutRemainingMillis = lockoutRemainingMillis,
                        onDeal = controller::deal,
                        onHit = controller::hit,
                        onStand = controller::stand,
                        onAccessGranted = controller::releaseAccess,
                        onPlayAgain = controller::playAgain,
                    )
                }
            }
        }

        // Release the blocker the moment access is earned.
        unlockWatcher = scope.launch {
            controller.unlocked.collect { unlockedPkg ->
                if (unlockedPkg == pkg) dismiss(reason = "access granted") 
            }
        }

        return composeView
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // NOT_FOCUSABLE: Home/Back/gestures keep working (no key capture, no IME
        // stealing) while touches on the opaque surface are still consumed.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.OPAQUE,
    ).apply {
        gravity = Gravity.CENTER
        // Debug label only; makes the window easy to spot in `dumpsys window`.
        title = "SonderBlocker"
    }

    companion object {
        private const val TAG = "SonderBlocker"
    }
}
