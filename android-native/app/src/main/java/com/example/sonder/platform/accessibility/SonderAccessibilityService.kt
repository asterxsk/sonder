package com.example.sonder.platform.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.example.sonder.BuildConfig
import com.example.sonder.domain.ForegroundSurface
import com.example.sonder.platform.enforcement.EnforcementCoordinator
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

    /** Reference to the platform's audio service, for [pauseMedia]. */
    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /** Reused across probes: allocating a Rect per window per pass is per-pass garbage. */
    private val pipBounds = Rect()

    /** The thread the platform delivered this service's windows and nodes on. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * When a content-change event was last passed to the coordinator; see [onContentChanged]
     * for why the stream is coalesced before it reaches the decision path. Touched only from
     * the service's own callback thread.
     */
    private var lastContentEventAtMillis = 0L

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
        // The declared info is edited in place rather than replaced with a fresh
        // AccessibilityServiceInfo. `serviceInfo = AccessibilityServiceInfo()` handed the
        // framework a service whose capabilities were empty, and canRetrieveWindowContent is
        // a *capability*, not a flag: it is parsed from the XML by the system at bind time
        // and there is no runtime counterpart to set it back, so a replaced info silently
        // took window-content access away from a service the user had granted it to.
        //
        // The cost of that was invisible and total for the scoped targets: every
        // AccessibilityWindowInfo.root came back null, so showsMarkers found no tree to walk
        // and answered "not the surface" for every app, every pass — Instagram's Reels and
        // YouTube's Shorts were never detected, and a shorts-scoped target therefore never
        // gated anything at all, while a whole-app target (which reads no window content)
        // went on working. The XML is the declaration and the only place the capability can
        // be granted; this method now only top-ups what the XML already said.
        //
        // FLAG_REPORT_VIEW_IDS is what populates AccessibilityNodeInfo.viewIdResourceName,
        // which is the only thing showsMarkers matches on — without it every node reports a
        // null id and no scoped target could ever detect its surface either.
        // Never a guard: everything below this point has to run whether or not the declared
        // info could be read, and a service that returned here would be connected but deaf.
        val declared = serviceInfo
        if (declared != null) {
            declared.flags = declared.flags or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            declared.notificationTimeout = EVENT_DEBOUNCE_MILLIS
            serviceInfo = declared
            // The one fault in this file that cannot be fixed from here: the capability is the
            // XML's to grant and there is no setter for it, so a service running without it
            // can read no window tree and no scoped target can ever recognise its surface.
            // Said out loud rather than left to be inferred from a gate that never fires.
            if (!declared.canRetrieveWindowContent) {
                Log.w(TAG, "no window-content capability: scoped surfaces cannot be detected")
            }
        }
        registerScreenOffReceiver()
        // The event stream is the fast path; this re-checks the real foreground for the
        // events it never delivers (see EnforcementCoordinator.onServiceConnected).
        coordinator.onServiceConnected(
            windows = ForegroundWindows(::isBehindAnotherWindow),
            activePackage = ActiveWindowPackage(::activePackage),
            content = ActiveWindowContent(::showsMarkers),
            appCloser = AppCloser(::closePackage),
            mediaPauser = MediaPauser(::pauseMedia),
            pipWindows = PipWindows(::hasPipWindow),
        )
        AccessibilityGate.onServiceConnected(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> Unit
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                onContentChanged(event)
                return
            }
            else -> return
        }
        val pkg = event.packageName?.toString() ?: return
        // A window event is only evidence while its window still exists. Events are delivered
        // in bursts and can arrive seconds after the fact — measured on an emulator, a frame
        // of the app the user had just left arrived 2.3s after Home with that app holding no
        // windows at all — and a decision made on one decides for an app that is not on screen.
        // For a gated app with an empty bank that is a gate raised over the launcher, torn down
        // again by the next re-check: a flash over the screen the user was watching, from an
        // event about a window that had already gone. The window list answers this in the same
        // read the decision does anyway, and an event naming a window that no longer exists is
        // not evidence about anything. A *departing* window still exists when its event
        // arrives, which is what keeps the release offered to Home out of this check.
        if (windows?.any { it.id == event.windowId } == false) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "STALE_EVENT_IGNORED(pkg=$pkg window=${event.windowId})")
            }
            return
        }
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
     * The second event the blocker needs, and the one the first cannot stand in for: a
     * scoped surface is reached *inside* an Activity.
     *
     * Instagram's Reels is a fragment of the main tab Activity and YouTube's Shorts is a
     * fragment of its player, so switching the feed for the viewer resumes nothing and opens
     * no window: the window-state event that names the app is never delivered again, and the
     * whole transition is invisible to it. What the platform does deliver is that a window's
     * *content* changed, which is that same transition seen from the tree. Without it a
     * scoped target is only ever recognised by the coordinator's periodic re-check, and the
     * re-check is settled while its own blocker is up — so the surface arriving while the
     * user is already inside the app is exactly the case nothing would catch.
     *
     * Content-change events are a firehose: every window on the device emits them for every
     * frame of every list that moves. Three filters stand in front of the coordinator so the
     * decision path sees a trickle instead:
     *
     *  - the package must be an *enabled target*, answered from the warm cache, so every
     *    other app on the device costs one map lookup;
     *  - the package must be the one whose window is in front. A background app's list
     *    updating is not the user going anywhere, and deciding for it would act on a surface
     *    that is not on screen — raising the blocker over the app they are actually using;
     *  - what is left is coalesced to one event per [CONTENT_EVENT_MIN_INTERVAL_MILLIS]:
     *    far faster than a person can open a surface, and slow enough that a scrolling feed
     *    costs a couple of probes a second instead of one per frame.
     *
     * The class name is deliberately not forwarded. On a content-change event it is the
     * *View* class that changed (`android.widget.FrameLayout`), and the coordinator reads the
     * class name as the Activity's, to tell this app's own Activities apart — a view name
     * handed to it there is evidence about nothing.
     */
    private fun onContentChanged(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (!coordinator.isEnabledTarget(pkg)) return
        val now = SystemClock.uptimeMillis()
        if (now - lastContentEventAtMillis < CONTENT_EVENT_MIN_INTERVAL_MILLIS) return
        // Checked before the stamp is written, so a target updating in the background does
        // not spend the interval the app in front would have used.
        if (activePackage() != pkg) return
        lastContentEventAtMillis = now
        coordinator.onForeground(pkg = pkg, className = null, windowId = event.windowId)
    }

    /**
     * Runs [block] where the platform's window list is valid to read, and answers [fallback]
     * if that does not happen in time.
     *
     * `AccessibilityService.windows`, `AccessibilityWindowInfo.root` and every
     * `AccessibilityNodeInfo` are owned by the thread the event was delivered on — the
     * service's own main thread here. The readers below are called from the enforcement
     * coordinator's single-threaded dispatcher, which is a different thread, and reading
     * window content from one is outside the contract: the window list can come back empty
     * and a root that plainly exists can come back null. Measured on a Pixel emulator, that
     * is exactly what happened — YouTube's Shorts tree was there to be read and the probe
     * said it was not, and the gate went up and came down twice a second over a floating
     * video, because the same unreliable answer was deciding both.
     *
     * So the read is posted to the main thread and the caller waits for it. The wait is
     * bounded and the caller is a background dispatcher, never the main thread — the service
     * hands the coordinator its callbacks through `scope.launch`, so nothing on the main
     * thread is ever waiting on the thread this is waiting from. [fallback] is the answer for
     * a main thread too busy to reply within [PROBE_TIMEOUT_MILLIS], which is the direction
     * each caller already chose for an unreadable window.
     */
    private fun <T> onServiceThread(fallback: T, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()

        val latch = CountDownLatch(1)
        var answer = fallback
        if (!mainHandler.post {
                try {
                    answer = block()
                } catch (_: Throwable) {
                    // A detached node or a service torn down mid-walk: keep the fallback.
                } finally {
                    latch.countDown()
                }
            }
        ) {
            // Looper already quitting; no reader will run.
            return fallback
        }
        // The latch is the only guarantee that `answer` is visible to this thread.
        return if (latch.await(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) answer else fallback
    }

    private fun isBehindAnotherWindow(windowId: Int): Boolean =
        onServiceThread(fallback = false) { readIsBehindAnotherWindow(windowId) }

    private fun readIsBehindAnotherWindow(windowId: Int): Boolean {
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
    private fun activePackage(): String? =
        onServiceThread(fallback = null) { readActivePackage() }

    private fun readActivePackage(): String? = runCatching {
        val focused = windows?.firstOrNull { it.isActive } ?: return@runCatching null
        if (focused.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return@runCatching null
        if (focused.type == AccessibilityWindowInfo.TYPE_SYSTEM) return@runCatching null
        focused.root?.packageName?.toString()
    }.getOrNull()

    /**
     * Whether the active window carries any of [markers] in its node tree — see
     * [ActiveWindowContent], which is the contract this implements.
     *
     * Runs on the service's own thread; see [onServiceThread] for why every reader below is
     * marshalled there rather than answering where the caller happens to be.
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
        return onServiceThread(fallback = false) { readMarkers(packageName, markers) }
    }

    private fun readMarkers(packageName: String, markers: List<String>): Boolean =
        runCatching {
            // The app's own windows, so a stale tree left over from a window the user has
            // already left cannot answer about the wrong app.
            val listed = windows ?: return@runCatching false
            // Our own blocker, which is the window that blinds the check below.
            val coveredByUs = listed.any {
                it.root?.packageName?.toString() == this.packageName
            }
            // Every window of the app is asked, the focused one first.
            //
            // Not just the first window of the package: an app can hold more than one at a
            // time — a round trip out to Recents and back is enough to leave the task with a
            // second one — and the list's order is not a promise that the first of them is
            // the one with the screen. Nor is the surface guaranteed to share a window with
            // the rest of the app: a sheet, a picture-in-picture video or the app's own
            // overlay sits in a window of its own, and asking a single window reports "not
            // the surface" about an app plainly showing one. The focused window is asked
            // first because it is the one taking input, so it is the one whose tree the user
            // is looking at.
            //
            // The count is small — an app on screen holds one window, sometimes two — so the
            // cost of asking all of them is a walk of the trees already in hand, not a new
            // query.
            val candidates = listed
                .filter { it.root?.packageName?.toString() == packageName }
                // Stable, so windows the platform did not rank keep the list's own order.
                .sortedByDescending { it.isActive }

            var unreadable = 0
            val report = WalkReport()
            val found = candidates.any { window ->
                val root = window.root
                if (root == null) {
                    unreadable++
                    return@any false
                }
                // The invisibility relaxation is offered only to the window that holds input.
                // It exists because our blocker makes the *live* surface report itself
                // invisible; a marker sitting in a window the app has behind the one on screen
                // is a leftover, not a surface, and counting it would keep the blocker up over
                // a screen the user has already left.
                walkForMarker(
                    root = root,
                    packageName = packageName,
                    markers = markers,
                    coveredByUs = coveredByUs && window.isActive,
                    report = report,
                )
            }
            if (found) {
                // Forget the miss, so arriving at a different markerless screen later is
                // reported again rather than deduplicated against a stale one.
                lastMiss.remove(packageName)
            } else if (BuildConfig.DEBUG) {
                logMiss(packageName, report.describe(candidates.size))
            }
            // Every window of the app came back with no tree. That is either a window the
            // app is swapping out from under the walk, or the service having lost the
            // capability that makes any tree readable at all — and from here the two look
            // identical while only one of them is a code fault. Said out loud in debug
            // builds so a scoped target that never fires can be told apart from a marker
            // list that no longer matches, which is the other way this probe goes quiet.
            if (!found && candidates.isNotEmpty() && unreadable == candidates.size &&
                BuildConfig.DEBUG
            ) {
                Log.d(TAG, "SURFACE_PROBE_UNREADABLE(pkg=$packageName windows=${candidates.size})")
            }
            found
        }.getOrDefault(false)

    /**
     * The last miss reported per package, so [logMiss] can stay quiet about a screen that has
     * not changed.
     *
     * A scoped app that is *not* on its surface is re-probed on every re-check pass — that is
     * the point of the pass — so an unguarded miss line is several per second for as long as
     * the user reads their feed, which buries the one transition worth seeing. Keyed by
     * package and cleared the moment the surface is found, so the line appears once when the
     * app settles on a screen with no marker in it, and again if that screen later changes to
     * a different one.
     */
    private val lastMiss = ConcurrentHashMap<String, String>()

    /** Reports a miss once per distinct tree, rather than once per pass. */
    private fun logMiss(packageName: String, detail: String) {
        if (lastMiss.put(packageName, detail) == detail) return
        Log.d(TAG, "SURFACE_PROBE_MISS($detail)")
    }

    /**
     * What a miss looked like, for the debug line [showsMarkers] prints when a scoped target
     * does not fire.
     *
     * A probe that goes quiet has three causes that are indistinguishable from outside: the
     * app held no window the service could read, the tree was read but the markers no longer
     * name anything in it, or the walk ran out of its node budget before reaching them. Kept
     * as one small object rather than three out-parameters so the walk signature stays a
     * question and this stays a note.
     */
    private class WalkReport {
        var visited = 0
        var capped = false

        /** Ids that matched a marker regardless of why they were rejected; the interesting miss. */
        val matches = mutableListOf<String>()

        /** The first ids seen at all, so a catalogue mismatch is visible in one line. */
        val names = mutableListOf<String>()

        fun describe(windows: Int): String = buildString {
            append("pkg-windows=").append(windows)
            append(" visited=").append(visited)
            if (capped) append(" CAPPED")
            append(" ids=").append(names.joinToString(","))
            if (matches.isNotEmpty()) append(" matched-but-hidden=").append(matches.joinToString(","))
        }

        companion object {
            /** How many ids of each kind are worth carrying into a log line. */
            const val SAMPLE = 14
        }
    }

    /**
     * Breadth-first walk of one window's tree for any of [markers]; see [showsMarkers] for
     * why the walk is breadth-first, why it is capped, and what counts as visible.
     */
    private fun walkForMarker(
        root: AccessibilityNodeInfo,
        packageName: String,
        markers: List<String>,
        coveredByUs: Boolean,
        report: WalkReport,
    ): Boolean {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.addLast(root)
        var visited = 0
        while (pending.isNotEmpty() && visited < MAX_NODES_VISITED) {
            val node = pending.removeFirst()
            visited++
            report.visited++
            val id = node.viewIdResourceName
            val matches = id != null && markers.any { id.contains(it, ignoreCase = true) }
            if (matches && BuildConfig.DEBUG && report.names.size < WalkReport.SAMPLE) {
                // Positional in the walk, not tree depth: what matters is whether the marker
                // was reached well inside the budget or right at its edge.
                report.matches += "$id(visible=${node.isVisibleToUser} at=$visited)"
            }
            if (id != null && matches && (node.isVisibleToUser || coveredByUs)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "SHORTS_SURFACE(pkg=$packageName id=$id)")
                return true
            }
            if (BuildConfig.DEBUG && id != null && report.names.size < WalkReport.SAMPLE) {
                report.names += id.substringAfterLast('/')
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { pending.addLast(it) }
            }
        }
        if (pending.isNotEmpty()) report.capped = true
        return false
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

    /**
     * Stop what the app in front is playing — see [MediaPauser], which is the contract this
     * implements.
     *
     * `KEYCODE_MEDIA_PAUSE` rather than `KEYCODE_MEDIA_PLAY_PAUSE`: the latter *toggles*, so
     * dispatching it at an app that is already paused would start it — the exact opposite of
     * what the blocker wants, and reachable whenever the user had paused the reel themselves
     * before the gate went up. A dedicated pause is a no-op on an app that is not playing.
     *
     * The key goes to whoever holds the media session, which is the app the user was watching:
     * our own overlay is deliberately not focusable, so it cannot be holding one. A failure
     * here is logged and dropped — the blocker is already up, and a video that keeps playing
     * is a nuisance, not a hole in the gate.
     */
    private fun pauseMedia() {
        runCatching {
            val now = android.os.SystemClock.uptimeMillis()
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE, 0),
            )
            audioManager.dispatchMediaKeyEvent(
                KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE, 0),
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "MEDIA_PAUSED")
        }.onFailure { Log.w(TAG, "media pause failed", it) }
    }

    /**
     * Whether [packageName] owns a window much smaller than the screen — see [PipWindows],
     * which is the contract this implements, including why the answer fails closed on an
     * unmeasurable window.
     *
     * Both dimensions are tested rather than the area: a PiP window is a small rectangle, and
     * a full-screen window with a big letterboxed video inside it is not one. The threshold is
     * two fifths of the screen in each direction, which is comfortably above Android's own
     * maximum PiP size and comfortably below any window a user would call "the app".
     *
     * The window's own package is read from its root, the same way [activePackage] reads it,
     * because the window-type check alone would answer about the *system's* PiP container
     * rather than about the app inside it.
     */
    private fun hasPipWindow(packageName: String): Boolean =
        onServiceThread(fallback = false) { readPipWindow(packageName) }

    private fun readPipWindow(packageName: String): Boolean = runCatching {
        val listed = windows ?: return@runCatching false
        val metrics = resources.displayMetrics
        val maxWidth = metrics.widthPixels * PIP_MAX_SCREEN_FRACTION
        val maxHeight = metrics.heightPixels * PIP_MAX_SCREEN_FRACTION
        listed.any { window ->
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) return@any false
            if (window.root?.packageName?.toString() != packageName) return@any false
            window.getBoundsInScreen(pipBounds)
            pipBounds.width() in 1..maxWidth.toInt() && pipBounds.height() in 1..maxHeight.toInt()
        }
    }.getOrDefault(false)

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
         * How often one content-change event may reach the coordinator; see
         * [onContentChanged]. A quarter of a second: short enough that a surface is caught
         * while the user is still arriving at it, long enough that a feed in motion costs a
         * few probes a second rather than one per frame.
         */
        const val CONTENT_EVENT_MIN_INTERVAL_MILLIS = 250L

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

        /**
         * How long a background caller waits for a window read to come back from the service
         * thread; see [onServiceThread].
         *
         * Generous next to the work it is waiting on — a bounded walk of a tree already in
         * hand, which measures in single-digit milliseconds — and short next to the 500 ms
         * re-check interval, so a main thread that misses the deadline delays one pass rather
         * than wedging the loop.
         */
        const val PROBE_TIMEOUT_MILLIS = 150L

        /**
         * How much of the screen a window may cover and still count as picture-in-picture.
         *
         * Two fifths in each direction: above Android's own maximum PiP size and far below
         * any window a user would call "the app". Asserting both dimensions is what keeps a
         * full-screen window with a letterboxed video inside it from reading as a float.
         */
        private const val PIP_MAX_SCREEN_FRACTION = 0.4f
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
