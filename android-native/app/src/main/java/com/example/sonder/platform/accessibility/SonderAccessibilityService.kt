package com.example.sonder.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.example.sonder.BuildConfig
import com.example.sonder.domain.ForegroundSurface
import com.example.sonder.platform.enforcement.EnforcementCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground detection for the blocker (plan §3).
 *
 * Every event is classified into a [ForegroundSurface]: transient system
 * surfaces are ignored, home/recents and the detox app release the blocker, and
 * real apps are gated when they are blocked targets. Launchers and the blocker
 * itself therefore no longer need to be filtered out *by package name* to avoid
 * false gating — instead they are handled by the decision layer, which is what
 * lets the blocker reliably disappear when the user goes Home.
 *
 * The service also holds the capabilities that need to live inside a connected
 * accessibility service, and hands them to the coordinator as plain interfaces
 * ([ForegroundWindows], [ActiveWindowPackage], [ActiveWindowContent], [AppCloser]) so no
 * policy lives here.
 *
 * **What is read, and what is not.** The event stream is still read for package name, class
 * name and window id only. The node tree is read for *view identifiers* and nothing else,
 * and only for the one app the coordinator names, only when that app is a scoped target,
 * and only as a yes/no against markers the coordinator supplies — see [ActiveWindowContent].
 * No text, no content descriptions, no images, nothing retained. `canRetrieveWindowContent`
 * is therefore on, which is a real widening of the service's reach even though nothing
 * content-bearing is ever looked at, and `accessibility_service_description` says so in the
 * terms the user consents to.
 *
 * [notificationTimeout] stays tiny: it is the delay Android may add before
 * delivering an event, and the blocker must land while the app is still coming
 * up.
 */
@AndroidEntryPoint
class SonderAccessibilityService : AccessibilityService() {

    @Inject lateinit var coordinator: EnforcementCoordinator

    private var receiverRegistered = false

    /** Reference to the platform's process manager, for [closePackage]. */
    private val activityManager by lazy {
        getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    /**
     * Screen off = the blocker must go: it must never cover the keyguard, and there is
     * nothing on screen to enforce against. Screen on starts that enforcement again.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> coordinator.onScreenOff()
                Intent.ACTION_SCREEN_ON -> coordinator.onScreenOn()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            // This assignment replaces the whole flag word the XML declared, so every flag
            // the service relies on has to be repeated here. FLAG_REPORT_VIEW_IDS is what
            // populates AccessibilityNodeInfo.viewIdResourceName, which is the only thing
            // showsMarkers matches on — without it every node reports a null id and no
            // scoped target could ever detect its surface.
            flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = EVENT_DEBOUNCE_MILLIS
        }
        registerScreenOffReceiver()
        // The event stream is the fast path; this re-checks the real foreground for the
        // events it never delivers (see EnforcementCoordinator.onServiceConnected).
        coordinator.onServiceConnected(
            windows = ForegroundWindows(::isBehindAnotherWindow),
            activePackage = ActiveWindowPackage(::activePackage),
            content = ActiveWindowContent(::showsMarkers),
            appCloser = AppCloser(::closePackage),
        )
        AccessibilityGate.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // The class name separates our own Activities (the detox app opening,
        // which must release the blocker) from our own overlay window (the
        // blocker itself, whose event must never dismiss it).
        val className = event.className?.toString()
        // The service reports what it saw; the coordinator classifies it. One input the
        // classification needs — whether this package is an enabled target — lives on the
        // enforcement cache, and it is what stops the launcher/IME hints from exempting a
        // target the user chose to gate.
        coordinator.onForeground(
            pkg = pkg,
            className = className,
            // Carried along so a release can be checked against the window that reported
            // it: see ForegroundWindows.
            windowId = event.windowId,
        )
    }

    /**
     * The window list, top-most first, answering "is the window that reported a release
     * still the one in front" from metadata alone — no window content is read.
     */
    private fun isBehindAnotherWindow(windowId: Int): Boolean {
        val listed = try {
            windows
        } catch (_: Throwable) {
            // The service can be disconnected mid-call; no answer is better than a guess.
            return false
        } ?: return false

        // Our own overlay is deliberately not focusable, so while it covers a blocked app
        // the app's window keeps input focus: this answers "is that app still in front"
        // even with the blocker in the way.
        val focused = listed.firstOrNull { it.isActive } ?: return false

        // Only another *application* having focus is evidence. A system window or a
        // keyboard holding focus while a launcher event lands says nothing about where
        // the user is, and holding the blocker up on that would strand it over Home.
        if (focused.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return false
        if (focused.type == AccessibilityWindowInfo.TYPE_SYSTEM) return false

        return focused.id != windowId && listed.any { it.id == windowId }
    }

    /**
     * The package of the window holding input focus — see [ActiveWindowPackage], which is
     * the contract this implements.
     *
     * The IME and system windows are refused for the same reason [isBehindAnotherWindow]
     * refuses them: a keyboard holding focus says nothing about which app is on screen,
     * and answering with its package would deny a release the user really did make. A null
     * answer is the honest one there.
     *
     * A window whose root cannot be read is also null: the list gives metadata even when the
     * tree is unavailable, and a window with no readable root cannot name its package.
     */
    private fun activePackage(): String? = runCatching {
        val focused = windows?.firstOrNull { it.isActive } ?: return@runCatching null
        if (focused.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return@runCatching null
        if (focused.type == AccessibilityWindowInfo.TYPE_SYSTEM) return@runCatching null
        focused.root?.packageName?.toString()
    }.getOrNull()

    /**
     * Whether the active window carries any of [markers] in its node tree — see
     * [ActiveWindowContent], which is the contract this implements.
     *
     * Read from the window list rather than from the active root. While the blocker covers a
     * scoped target the *active* window can be the blocker's own — it is not focusable, but
     * an active root is not the same question — and answering from it says "not the surface"
     * about an app that is plainly showing one. That answer takes the blocker down and the
     * next pass raises it again: a flash loop, with the app gated and ungated every 500ms.
     * Naming the app's own window asks the question that was meant, and it also enforces
     * that the tree belongs to the app the caller asked about, which the active-root check
     * used to do by hand.
     *
     * Breadth-first rather than depth-first, because the container that names the surface is
     * a shell near the top of the tree: a depth-first walk spends its whole budget inside the
     * first branch it picks, and the cap would then decide the answer instead of the tree.
     *
     * Only *visible* nodes count. A marker already in the tree is not evidence that the
     * surface is on screen: a view left behind by a fragment the user has navigated away
     * from is still in the tree, and matching it is exactly the failure this walk exists to
     * avoid, in the direction that blocks the app the target was told to leave alone. The
     * subtree is still descended — visibility is the node's own answer and a container's
     * children are not always invisible with it — so a hidden branch only costs its nodes
     * against the cap.
     *
     * Visibility carries no information at all while our own blocker covers the app, and
     * that is measured rather than assumed. On YouTube, `reel_recycler` reports
     * `isVisibleToUser=false` while the blocker is up and `true` the moment it goes — the
     * same node, the same screen bounds, only the covering window differing. So "invisible"
     * there does not mean the surface is gone; it means the blocker is doing its job. Left
     * as evidence of absence it releases the blocker, and the next pass raises it again:
     * the app gains and loses its blocker twice a second for as long as the user stays on
     * the surface. A marker therefore counts when the node is visible *or* when our own
     * window is the one covering it.
     *
     * The cost of that relaxation is that leaving the surface from behind the blocker —
     * pressing Back out of Shorts onto the feed — is not noticed, and the blocker stays
     * until the user leaves the app ([AppCloser]'s CLOSE control is the way out). That is
     * the direction to fail in: a blocker that waits to be dismissed is a nuisance, and the
     * alternative above is an app that cannot be used at all.
     *
     * Everything is inside one `runCatching`, so no failure mode here can reach the caller:
     * `getChild` throws once a node has been detached by the app mid-walk, which is ordinary
     * while a feed is scrolling, and the answer to an unreadable tree is "not the surface".
     * Nodes are deliberately not recycled — `AccessibilityNodeInfo.recycle` has been a no-op
     * since API 33 and calling it on a node the framework still holds is a use-after-free on
     * the releases that do pool.
     */
    private fun showsMarkers(packageName: String, markers: List<String>): Boolean {
        if (markers.isEmpty()) return false
        return runCatching {
            // The app's own window, top-most first, so a stale tree left over from a window
            // the user has already left cannot answer about the wrong app.
            val listed = windows ?: return@runCatching false
            val window = listed.firstOrNull {
                it.root?.packageName?.toString() == packageName
            } ?: return@runCatching false
            val root = window.root ?: return@runCatching false
            // Our own blocker, which is the window that blinds the check above.
            val coveredByUs = listed.any {
                it.root?.packageName?.toString() == this.packageName
            }

            val pending = ArrayDeque<AccessibilityNodeInfo>()
            pending.addLast(root)
            var visited = 0
            while (pending.isNotEmpty() && visited < MAX_NODES_VISITED) {
                val node = pending.removeFirst()
                visited++
                val id = node.viewIdResourceName
                if (
                    id != null &&
                    (node.isVisibleToUser || coveredByUs) &&
                    markers.any { id.contains(it, ignoreCase = true) }
                ) {
                    if (BuildConfig.DEBUG) Log.d(TAG, "SHORTS_SURFACE(pkg=$packageName id=$id)")
                    return@runCatching true
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { pending.addLast(it) }
                }
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Leave a gated app — see [AppCloser], which is the contract this implements, including
     * the note that the process kill is best-effort and the Home action is the real effect.
     *
     * `killBackgroundProcesses` needs `KILL_BACKGROUND_PROCESSES`, an install-time
     * permission no user is asked for. It can throw for a package that vanished mid-call, and
     * a failure there must not stop the Home action from having done its work, so it is
     * caught on its own.
     */
    private fun closePackage(packageName: String) {
        // Home first: it is the half that always works, and ordering it first means the user
        // is out of the app even if everything after it fails.
        performGlobalAction(GLOBAL_ACTION_HOME)
        runCatching { activityManager.killBackgroundProcesses(packageName) }
        if (BuildConfig.DEBUG) Log.d(TAG, "APP_CLOSED(pkg=$packageName)")
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        // The service is going away: take the blocker down with it.
        coordinator.onServiceStopped()
        unregisterScreenOffReceiver()
        AccessibilityGate.onServiceDisconnected()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        coordinator.onServiceStopped()
        unregisterScreenOffReceiver()
        AccessibilityGate.onServiceDisconnected()
        super.onDestroy()
    }

    private fun registerScreenOffReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        receiverRegistered = true
    }

    private fun unregisterScreenOffReceiver() {
        if (!receiverRegistered) return
        receiverRegistered = false
        runCatching { unregisterReceiver(screenReceiver) }
    }

    companion object {
        private const val TAG = "SonderGate"

        /** Max delay Android may add before delivering a window event. */
        const val EVENT_DEBOUNCE_MILLIS = 50L

        /**
         * How many nodes one scoped-surface probe may visit.
         *
         * The walk runs on the coordinator's serialized dispatcher and can run once per
         * foreground pass, so its cost has to be bounded by something other than the app's
         * tree size — an Instagram feed is thousands of nodes deep. The cap is generous
         * relative to where the viewer container sits (it is a shell near the root, which is
         * why the walk is breadth-first) and small enough that a pathological tree costs a
         * few milliseconds. Hitting it answers "not the surface", which lets the app through:
         * the cost of a miss is an ungated feed, and that is the direction to fail in.
         */
        const val MAX_NODES_VISITED = 400
    }
}

/** Static bridge so the UI can check whether the service is live (audit + onboarding). */
object AccessibilityGate {
    @Volatile
    private var connected: Boolean = false

    fun onServiceConnected(service: AccessibilityService) {
        connected = true
    }

    fun onServiceDisconnected() {
        connected = false
    }

    fun isLive(): Boolean = connected
}
