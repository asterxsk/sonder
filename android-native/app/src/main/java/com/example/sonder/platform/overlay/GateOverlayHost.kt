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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import com.example.sonder.BuildConfig
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.SonderTheme
import com.example.sonder.ui.gate.GateContent
import com.example.sonder.ui.gate.GateController
import com.example.sonder.ui.gate.TableState
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
 * At most one window exists at a time, and it is either fully on screen or gone:
 * [showGate] *ensures* it (a gate for the app already covered is a no-op, so a burst
 * of foreground events cannot stack blockers or reset a live hand, and a window
 * already up for another app is repointed rather than replaced), and [dismiss]
 * removes it. There is no in-between state — a blocker nobody is tracking is a
 * blocker nothing can dismiss, which is how a lockout screen once ended up stranded
 * over an unrelated app.
 *
 * The no-op above is only trusted while the window is really on screen (see
 * [detachListener]): a blocker the system removed has to be raisable again, or the app
 * it was covering stays uncovered for the rest of the session.
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

    /** The single blocker window, kept for the life of the service. */
    private var root: View? = null

    /** What [root] was composed for; a different request needs a new composition. */
    private var composedFor: ShowRequest? = null

    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var unlockWatcher: Job? = null

    /**
     * What a window was composed for. A gate whose package or lockout differs has to be
     * composed again; one that matches is re-attached as it stands, which is what keeps
     * a hand in progress exactly where the user left it.
     */
    private data class ShowRequest(
        val pkg: String,
        val label: String,
        /** Epoch millis the lockout ends at, or 0 when there is no lockout. */
        val lockoutUntilMillis: Long,
    )

    /**
     * Notes a blocker window the system has taken away behind this host's back — a
     * revoked overlay permission, a window token that died with the app underneath, a
     * display change that never came back. Without it the host would go on believing the
     * blocker is up and refuse to raise it again, which is how a blocked app ends up
     * uncovered for the rest of the session; with it, the next event or re-check pass
     * composes the window afresh.
     *
     * Only the current root clears the state, so the removal of a window this host has
     * already replaced cannot unmount its successor.
     */
    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit

        override fun onViewDetachedFromWindow(view: View) {
            if (root !== view) return
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "OVERLAY_DETACHED(pkg=$shownForPackage reason=taken-by-system)")
            }
            root = null
            composedFor = null
            shownForPackage = null
            teardownOwner()
        }
    }

    /** Package the blocker is covering right now, or null while it is released. */
    @Volatile
    var shownForPackage: String? = null
        private set

    fun showGate(pkg: String, label: String) = show(pkg, label, lockoutUntilMillis = 0L)

    /** @param untilMillis epoch millis the lockout ends at, or 0 when there is no lockout. */
    fun showLockout(pkg: String, label: String, untilMillis: Long) =
        show(pkg, label, untilMillis)

    private fun show(pkg: String, label: String, lockoutUntilMillis: Long) {
        mainHandler.post {
            val request = ShowRequest(pkg, label, lockoutUntilMillis)

            // ensureOverlay, not showOverlay: the app is already covered by this exact
            // panel, so this changes nothing. A burst of foreground events can neither
            // stack blockers nor reset a hand in progress, and the window has to still be
            // attached for that to hold.
            if (shownForPackage == pkg &&
                root?.isAttachedToWindow == true &&
                composedFor == request
            ) {
                if (BuildConfig.DEBUG) Log.d(TAG, "OVERLAY_ENSURE(pkg=$pkg already covering)")
                return@post
            }

            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "blocker not shown for $pkg: overlay permission missing")
                return@post
            }

            val restored = controller.state.value.targetPackage == pkg &&
                controller.state.value.phase != TableState.Phase.IDLE
            controller.begin(pkg)
            if (BuildConfig.DEBUG && restored) {
                Log.d(TAG, "BLACKJACK_STATE_RESTORED(pkg=$pkg phase=${controller.state.value.phase})")
            }

            // A live window is repointed in place rather than replaced. The panel changes
            // as the enforcement state moves on — a debt reaching its ceiling, a lockout
            // that has run out and handed the app back, a switch between two blocked apps
            // — and tearing the window down for each of those would blink the blocked app
            // back into view. Replacing is the fallback for a view that cannot be reused.
            if (root != null && repoint(pkg, request)) return@post

            create(pkg, request)
        }
    }

    /**
     * Reuse the window that is already on screen: same view, same owners, new content and
     * a fresh binding for the unlock watcher.
     *
     * Returns false when the live view cannot be reused, leaving the caller to compose a
     * replacement. This is only ever called for a window that is genuinely on screen — a
     * dismissed blocker is gone, not hidden — so reusing it cannot resurrect a stale one.
     */
    private fun repoint(pkg: String, request: ShowRequest): Boolean {
        val view = root as? ComposeView ?: return false
        if (!view.isAttachedToWindow) return false
        return try {
            view.setContent(contentFor(request))
            windowManager.updateViewLayout(view, layoutParams())
            composedFor = request
            shownForPackage = pkg
            watchUnlock(pkg)
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "OVERLAY_REPOINTED(pkg=$pkg lockoutUntil=${request.lockoutUntilMillis})")
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "blocker could not be repointed for $pkg; composing a new window", t)
            removeBlocker(reason = "repoint failed")
            false
        }
    }

    /**
     * Compose a blocker window for [request] and put it on screen.
     *
     * The composition is built *before* the window is added, so the blocker's first frame
     * is already painted: adding the window first would put an empty surface over the app
     * for the length of the composition.
     */
    private fun create(pkg: String, request: ShowRequest) {
        if (root != null) removeBlocker(reason = "replacing")

        val view = try {
            buildView(request).also { it.addOnAttachStateChangeListener(detachListener) }
        } catch (t: Throwable) {
            Log.e(TAG, "blocker could not be composed for $pkg", t)
            teardownOwner()
            return
        }
        watchUnlock(request.pkg)

        try {
            windowManager.addView(view, layoutParams())
            root = view
            composedFor = request
            shownForPackage = pkg
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "OVERLAY_CREATED(pkg=$pkg lockoutUntil=${request.lockoutUntilMillis})")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "blocker add failed for $pkg", t)
            teardownOwner()
        }
    }

    /** Release the blocker the moment access is earned. One watcher per live window. */
    private fun watchUnlock(pkg: String) {
        unlockWatcher?.cancel()
        unlockWatcher = scope.launch {
            controller.unlocked.collect { unlockedPkg ->
                if (unlockedPkg == pkg) dismiss(reason = "access granted")
            }
        }
    }

    private fun contentFor(request: ShowRequest): @Composable () -> Unit = {
        val state by controller.state.collectAsState()
        SonderTheme {
            GateContent(
                label = request.label,
                state = state,
                lockoutUntilMillis = request.lockoutUntilMillis,
                onDeal = controller::deal,
                onHit = controller::hit,
                onStand = controller::stand,
                onAccessGranted = controller::releaseAccess,
                onPlayAgain = controller::playAgain,
            )
        }
    }

    /** Take the blocker off the screen. Safe to call when nothing is showing. */
    fun dismiss(reason: String = "unspecified") {
        mainHandler.post { removeBlocker(reason) }
    }

    /**
     * The window is removed, never merely hidden.
     *
     * Shrinking or fading a window instead of removing it leaves a blocker on screen that
     * no state in this host describes: Android does not have to honour a relayout the way
     * you expect, and a window left in a frame the screen no longer matches is a blocker
     * nothing can dismiss — which is exactly how a lockout screen ended up stranded over
     * an unrelated app. Removal cannot strand anything, and it is what makes "the blocker
     * is gone" a fact rather than a hope.
     *
     * Idempotent: calling it when nothing is showing does nothing.
     */
    private fun removeBlocker(reason: String) {
        val view = root
        if (view == null) {
            teardownOwner()
            return
        }

        val pkg = shownForPackage
        root = null
        composedFor = null
        shownForPackage = null
        try {
            windowManager.removeView(view)
        } catch (_: Throwable) {
            // Already detached by the system (config change, window token loss).
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "OVERLAY_REMOVED(pkg=$pkg reason=$reason)")
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

    private fun buildView(request: ShowRequest): View {
        // Compose without an Activity: the blocker owns the view-tree owners.
        val owner = OverlayLifecycleOwner().also { it.start() }
        lifecycleOwner = owner

        return ComposeView(context).apply {
            attachViewTreeOwners(this, owner)
            // Opaque background: no frame of the blocked app may show through.
            setBackgroundColor(PixelPalette.Bg.toArgb())
            setContent(contentFor(request))
        }
    }

    /** Full screen, opaque, touchable: the blocker covering the app. */
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
