package app.sonder.sonder

import android.accessibilityservice.AccessibilityService
import android.content.Intent

import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.catch

private const val TAG = "SonderA11y"

/**
 * Sonder accessibility service.
 *
 * Responsibilities:
 * - Observes window/foreground events and classifies the foreground package.
 * - For built-in targets (youtubeShorts/instagramReels) uses a conservative
 *   best-effort view-id classifier; never claims reliable surface detection.
 * - For custom wholeApp targets, any foreground of that package is an intercept.
 * - Maintains the 20s continuous-background revocation natively so enforcement
 *   works while Flutter is suspended.
 * - Shows/removes [GateOverlayController] based on the DataStore decision
 *   (lock > grant > needsChallenge), and emits the prescribed EventChannel events.
 *
 * The Flutter bridge owns the canonical game/policy; this service mirrors a
 * compact DataStore snapshot for enforcement while Flutter is suspended.
 */
class SonderAccessibilityService : AccessibilityService() {

    private lateinit var sessionStore: SessionStore
    private lateinit var overlayController: GateOverlayController
    private lateinit var appCatalog: AppTargetCatalog

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var serviceJob: Job? = null

    @Volatile private var currentForegroundPackage: String? = null
    @Volatile private var currentForegroundSignals = AccessibilitySignals(emptySet(), emptySet())
    @Volatile private var snapshotsCache: Map<String, EnforcementSnapshot> = emptyMap()

    // Tracks per-target lastBackground timestamps for 20s revocation.
    // Key is "pkg::surface".
    private val backgroundTimers: MutableMap<String, Long> = mutableMapOf()
    private val abandonmentJobs: MutableMap<String, Job> = mutableMapOf()

    // Debounce: do not re-interrupt the same package within this window.
    private var lastInterceptKey: String? = null
    private var lastInterceptAt: Long = 0L
    private val interceptDebounceMs: Long = 1500L

    // Monotonic tick for abandonment checks — 1s granularity.
    private var abandonmentTickJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "SonderAccessibilityService connected; package=${applicationContext.packageName}")
        sessionStore = SessionStore(applicationContext)
        overlayController = GateOverlayController(applicationContext)
        appCatalog = AppTargetCatalog(applicationContext)

        // Notify channel plugin that service state changed.
        SonderChannelPlugin.notifyServiceStateChanged(
            accessibilityEnabled = true,
            overlayAvailable = GateOverlayController.isOverlayAvailable(applicationContext),
        )

        // Keep a live cache of snapshots for fast foreground decisions.
        serviceJob = serviceScope.launch {
            sessionStore.snapshotsFlow
                .catch { error ->
                    Log.e(TAG, "Snapshot cache sync failed; clearing native cache", error)
                    emit(emptyList())
                }
                .collect { list ->
                snapshotsCache = list.associateBy { keyFor(it.packageName, it.surface) }
                Log.i(TAG, "Snapshot cache synced: ${list.size} target(s), packages=${list.map { it.packageName }.distinct()}")
                // Re-evaluate current foreground against new snapshots (e.g. after win/loss sync).
                currentForegroundPackage?.let { pkg ->
                    handleForegroundPackage(pkg, currentForegroundSignals)
                }
            }
        }

        // Periodic abandonment sweep (covers case where user backgrounds app and stays away).
        abandonmentTickJob = serviceScope.launch {
            while (true) {
                delay(1000)
                sweepAbandonedGrants(System.currentTimeMillis())
            }
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        Log.i(TAG, "SonderAccessibilityService unbinding; cachedTargets=${snapshotsCache.size}")
        if (::overlayController.isInitialized) overlayController.remove()
        serviceJob?.cancel()
        abandonmentTickJob?.cancel()
        abandonmentJobs.values.forEach { it.cancel() }
        abandonmentJobs.clear()
        backgroundTimers.clear()
        SonderChannelPlugin.notifyServiceStateChanged(
            accessibilityEnabled = false,
            overlayAvailable = false,
        )
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        if (::overlayController.isInitialized) overlayController.remove()
        serviceScope.cancel()
        Log.i(TAG, "SonderAccessibilityService destroyed")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            -> {
                val eventPkg = event.packageName?.toString()
                val root = rootInActiveWindow
                val rootPkg = root?.packageName?.toString()
                val configuredPackages = snapshotsCache.values.map { it.packageName }.toSet()
                // Prefer a configured event package; otherwise use the configured root
                // package. This handles window events emitted by system/child windows.
                val resolvedPkg = ForegroundPackageResolver.resolve(eventPkg, rootPkg, configuredPackages)
                    ?: return
                Log.d(TAG, "Accessibility event: type=${event.eventType}, eventPackage=${normalizePackageId(eventPkg)}, rootPackage=${normalizePackageId(rootPkg)}, resolved=$resolvedPkg")
                val signals = collectAccessibilitySignals(root)
                handleForegroundPackage(resolvedPkg, signals)
            }
            else -> Unit
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "SonderAccessibilityService interrupted")
    }

    /**
     * Core state machine invoked on each window event.
     *
     * Package-level custom targets (wholeApp) reliably gate on package alone.
     * Built-ins additionally attempt best-effort surface classification but
     * never gate a non-matching surface — inconclusive classification means
     * treat as package-level foreground without Shorts/Reels overlay (see
     * [SurfaceClassifier] docs).
     */
    internal fun handleForegroundPackage(packageName: String, signals: AccessibilitySignals) {
        val normalizedPackage = normalizePackageId(packageName) ?: return
        val now = System.currentTimeMillis()
        currentForegroundSignals = signals
        val previous = currentForegroundPackage

        // Background transition: previous package left foreground.
        if (previous != null && previous != normalizedPackage) {
            handlePackageBackground(previous, now)
            overlayController.onForegroundPackageChanged(normalizedPackage)
        }

        currentForegroundPackage = normalizedPackage
        currentForegroundSignals = signals

        // Foreground transition for the new package.
        handlePackageForeground(normalizedPackage, now)

        // Emit targetForeground for any configured snapshot matching this package
        // (Flutter uses it to recordForeground and keep grant alive).
        val matchingSnapshots = snapshotsForPackage(normalizedPackage)
        for (snap in matchingSnapshots) {
            // Determine the surface to report: for youtube/instagram, try best-effort
            // classification; for custom wholeApp, always wholeApp.
            val surfaceToReport = when (snap.surface) {
                TargetSurface.wholeApp -> TargetSurface.wholeApp
                TargetSurface.youtubeShorts, TargetSurface.instagramReels -> {
                    // Only report Shorts/Reels foreground if classifier confirms it.
                    // Otherwise we still emit foreground as wholeApp? No — we emit
                    // only the surface that the snapshot was registered for. For
                    // built-ins the effective gate is the specific surface; if
                    // classifier is inconclusive we do not gate and we emit a
                    // plain foreground for the general YouTube/Instagram app.
                    val classified = SurfaceClassifier.classify(
                        normalizedPackage,
                        signals.viewIds,
                        signals.textSignals,
                    )
                    if (classified == snap.surface) snap.surface else null
                }
            }
            if (surfaceToReport != null) {
                SonderChannelPlugin.sendTargetForeground(
                    packageName = snap.packageName,
                    surface = surfaceToReport.name,
                    atEpochMs = now,
                )
            }
        }

        // Gating decision: find the most relevant snapshot for this package.
        // Priority: lock > grant > needsChallenge, but that is evaluated per-snapshot;
        // we pick the configured snapshot that matches the inferred surface, falling
        // back to wholeApp snapshot if present and no surface-specific match.
        if (normalizedPackage == normalizePackageId(applicationContext.packageName)) {
            overlayController.remove()
            return
        }

        val candidate = selectGatingSnapshot(normalizedPackage, signals)
        if (candidate == null) {
            // Not a configured target — ensure no overlay remains if we were gating this pkg.
            // (onForegroundPackageChanged already handled the previous target case.)
            return
        }

        val currentSnap = snapshotsCache[keyFor(candidate.packageName, candidate.surface)] ?: candidate
        // Apply lazy abandonment expiry before deciding.
        val effectiveSnap = expireAbandonedGrantIfNeeded(currentSnap, now)
        val decision = evaluateAccess(effectiveSnap, now)

        when (decision.status) {
            AccessStatus.allowed -> {
                overlayController.remove()
                // Still interceptions are not needed while allowed.
            }
            AccessStatus.locked, AccessStatus.needsChallenge -> {
                maybeIntercept(effectiveSnap, decision, now)
            }
        }
    }

    private fun handlePackageForeground(packageName: String, now: Long) {
        // Clear background timer for any surface of this package (user returned).
        val keysToRemove = backgroundTimers.keys.filter { it.startsWith("$packageName::") }
        for (k in keysToRemove) {
            backgroundTimers.remove(k)
            abandonmentJobs[k]?.cancel()
            abandonmentJobs.remove(k)
        }
        // Persist foreground clears (recordForeground) for snapshots with a grant.
        // Do this lazily on next decision; also emit events already handled above.
        // But also update DataStore so abandonment timer is cleared even while Flutter suspended.
        for (snap in snapshotsForPackage(packageName)) {
            if (snap.lastBackgroundEpochMs != null) {
                serviceScope.launch {
                    val updated = recordForeground(snap, now)
                    sessionStore.updateSnapshot(updated)
                }
            }
        }
        // Emit for Flutter
        // (already emitted per-surface above; this is a package-level fallback for wholeApp)
    }

    private fun handlePackageBackground(packageName: String, now: Long) {
        // Record background for any snapshot of this package that has a grant.
        for (snap in snapshotsForPackage(packageName)) {
            if (snap.grantedUntilEpochMs == null) continue
            val key = keyFor(snap.packageName, snap.surface)
            backgroundTimers[key] = now
            // Schedule revocation check for this specific grant.
            abandonmentJobs[key]?.cancel()
            abandonmentJobs[key] = serviceScope.launch {
                delay(ABANDON_THRESHOLD_MS)
                sweepAbandonedGrants(System.currentTimeMillis())
            }
            // Persist lastBackground.
            serviceScope.launch {
                val updated = recordBackground(snap, now)
                sessionStore.updateSnapshot(updated)
            }
            SonderChannelPlugin.sendTargetBackground(
                packageName = snap.packageName,
                surface = snap.surface.name,
                atEpochMs = now,
            )
        }
    }

    private fun sweepAbandonedGrants(now: Long) {
        val toRevoke = mutableListOf<EnforcementSnapshot>()
        for ((key, bgAt) in backgroundTimers.toMap()) {
            if (now - bgAt >= ABANDON_THRESHOLD_MS) {
                val snap = snapshotsCache[key] ?: continue
                val revoked = expireAbandonedGrant(snap, now)
                if (revoked != snap) toRevoke.add(revoked)
            }
        }
        for (revoked in toRevoke) {
            val key = keyFor(revoked.packageName, revoked.surface)
            backgroundTimers.remove(key)
            abandonmentJobs[key]?.cancel()
            abandonmentJobs.remove(key)
            // If the revoked grant was for the current foreground target, dismiss overlay gating.
            serviceScope.launch { sessionStore.updateSnapshot(revoked) }
        }
    }

    private fun expireAbandonedGrantIfNeeded(snap: EnforcementSnapshot, now: Long): EnforcementSnapshot {
        val key = keyFor(snap.packageName, snap.surface)
        val bgAt = backgroundTimers[key] ?: snap.lastBackgroundEpochMs
        if (bgAt == null || snap.grantedUntilEpochMs == null) return snap
        if (now - bgAt >= ABANDON_THRESHOLD_MS) {
            val revoked = expireAbandonedGrant(snap.copyWithBg(bgAt), now)
            // Persist revocation eagerly so it survives while Flutter is suspended.
            if (revoked != snap) {
                serviceScope.launch { sessionStore.updateSnapshot(revoked) }
                backgroundTimers.remove(key)
            }
            return revoked
        }
        return snap
    }

    private fun EnforcementSnapshot.copyWithBg(bg: Long): EnforcementSnapshot =
        copy(lastBackgroundEpochMs = bg)

    private fun EnforcementSnapshot.copy(
        packageName: String = this.packageName,
        surface: TargetSurface = this.surface,
        grantedUntilEpochMs: Long? = this.grantedUntilEpochMs,
        lockedUntilEpochMs: Long? = this.lockedUntilEpochMs,
        lastBackgroundEpochMs: Long? = this.lastBackgroundEpochMs,
    ) = EnforcementSnapshot(packageName, surface, grantedUntilEpochMs, lockedUntilEpochMs, lastBackgroundEpochMs)

    private fun maybeIntercept(
        snapshot: EnforcementSnapshot,
        decision: AccessDecision,
        now: Long,
    ) {
        val key = keyFor(snapshot.packageName, snapshot.surface)
        // Debounce: same target within debounce window does not re-fire intercepted.
        if (lastInterceptKey == key && now - lastInterceptAt < interceptDebounceMs) {
            // Still ensure overlay is showing.
            overlayController.showOrUpdateForSnapshot(snapshot, decision, now)
            return
        }
        lastInterceptKey = key
        lastInterceptAt = now
        // Notify Flutter
        SonderChannelPlugin.sendTargetIntercepted(
            packageName = snapshot.packageName,
            surface = snapshot.surface.name,
            atEpochMs = now,
        )
        // Show overlay
        overlayController.showOrUpdateForSnapshot(snapshot, decision, now)
    }

    private fun snapshotsForPackage(packageName: String): List<EnforcementSnapshot> =
        snapshotsCache.values.filter { ForegroundPackageResolver.matches(it, packageName) }

    private fun selectGatingSnapshot(
        packageName: String,
        signals: AccessibilitySignals,
    ): EnforcementSnapshot? {
        val normalizedPackage = normalizePackageId(packageName) ?: return null
        val classified = SurfaceClassifier.classify(
            normalizedPackage,
            signals.viewIds,
            signals.textSignals,
        )
        val candidate = selectEnforcementSnapshot(
            foregroundPackage = normalizedPackage,
            snapshots = snapshotsCache.values.toList(),
            classifiedSurface = classified,
        )
        if (candidate != null) {
            Log.d(TAG, "Selected gate: package=$normalizedPackage surface=${candidate.surface}")
        }
        return candidate
    }

    private fun keyFor(packageName: String, surface: TargetSurface): String =
        "${normalizePackageId(packageName) ?: packageName.trim().lowercase()}::${surface.name}"

    internal data class AccessibilitySignals(
        val viewIds: Set<String>,
        val textSignals: Set<String>,
    )

    private fun collectAccessibilitySignals(root: AccessibilityNodeInfo?): AccessibilitySignals {
        if (root == null) return AccessibilitySignals(emptySet(), emptySet())
        val viewIds = mutableSetOf<String>()
        val textSignals = mutableSetOf<String>()
        collectSignalsRecursive(root, viewIds, textSignals, depth = 0)
        return AccessibilitySignals(viewIds, textSignals)
    }

    private fun collectSignalsRecursive(
        node: AccessibilityNodeInfo,
        viewIds: MutableSet<String>,
        textSignals: MutableSet<String>,
        depth: Int,
    ) {
        if (depth > 30) return
        node.viewIdResourceName?.let { viewIds.add(it) }
        node.text?.toString()?.let { textSignals.add(it) }
        node.contentDescription?.toString()?.let { textSignals.add(it) }
        node.className?.toString()?.let { textSignals.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSignalsRecursive(child, viewIds, textSignals, depth + 1)
            child.recycle()
        }
    }

    companion object {
        /** Used by tests / MainActivity to query service liveness without framework. */
        @Volatile var isRunning: Boolean = false
            private set
    }
}
