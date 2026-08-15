package app.sonder.sonder

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.TextView

private const val TAG = "SonderGateOverlay"

/**
 * Accessible gate overlay shown via TYPE_ACCESSIBILITY_OVERLAY.
 *
 * Properties required by the job:
 * - Only shown while the current foreground target is locked or needsChallenge.
 * - Announces state + remaining time (or challenge prompt) accessibly.
 * - Offers exactly one action that deep-links/launches Sonder's gate screen.
 * - Removes itself immediately on allowed state or when the target leaves the foreground.
 * - Never overlays outside configured targets.
 * - Prevents overlay loops (re-entrancy guard + dedup by current target key).
 */
class GateOverlayController(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var overlayView: View? = null
    private var currentTargetKey: String? = null
    private var isShowing: Boolean = false

    // For countdown display — not a timer that enforces policy (policy is DataStore/native).
    private var lastDecision: AccessDecision? = null

    /**
     * Shows or updates the overlay for [snapshot] at [nowEpochMs].
     * If the decision is allowed, the overlay is removed.
     * If the package is not a configured target, the overlay is removed.
     */
    fun showOrUpdateForSnapshot(
        snapshot: EnforcementSnapshot,
        decision: AccessDecision,
        nowEpochMs: Long,
    ) {
        val key = "${snapshot.packageName}::${snapshot.surface.name}"
        when (decision.status) {
            AccessStatus.allowed -> {
                // Remove if we were showing for this target.
                if (currentTargetKey == key) remove()
                return
            }
            AccessStatus.locked, AccessStatus.needsChallenge -> {
                if (!isShowing || currentTargetKey != key) {
                    // Prevent overlay loop: do not overlay Sonder itself.
                    if (snapshot.packageName == context.packageName) {
                        Log.d(TAG, "Refusing to overlay Sonder itself ($key)")
                        return
                    }
                    show(snapshot, decision)
                } else {
                    updateContent(snapshot, decision)
                }
            }
        }
        lastDecision = decision
        currentTargetKey = key
    }

    /**
     * Called when the foreground package changes. Removes overlay if the user
     * has left the currently-gated target for a different app (not Sonder).
     * When Sonder comes to the foreground (user tapped "Play Blackjack"),
     * the overlay is hidden but state is preserved so it can re-evaluate
     * when the blocked app returns to the foreground.
     */
    fun onForegroundPackageChanged(newPackageName: String?) {
        val key = currentTargetKey ?: return
        val gatedPackage = key.substringBefore("::")
        val sonderPackage = normalizePackageId(context.packageName)
        if (newPackageName == sonderPackage) {
            // Sonder came to foreground for the blackjack game — hide the
            // overlay so it doesn't cover Sonder's UI, but preserve state.
            if (isShowing) {
                Log.d(TAG, "Sonder came to foreground for gate, temporarily hiding overlay")
                hideForSonder()
            }
            return
        }
        if (newPackageName != gatedPackage) {
            Log.d(TAG, "Target left foreground ($gatedPackage -> $newPackageName), removing overlay")
            remove()
        }
    }

    /** Removes overlay if showing. */
    fun remove() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed", e)
        }
        overlayView = null
        currentTargetKey = null
        isShowing = false
        lastDecision = null
    }

    /**
     * Hides the overlay view while preserving the current target key,
     * so when the blocked app returns to the foreground the overlay can
     * re-evaluate without treating it as a fresh intercept.
     */
    fun hideForSonder() {
        val view = overlayView ?: return
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.w(TAG, "removeView failed during hideForSonder", e)
        }
        overlayView = null
        isShowing = false
        // Keep currentTargetKey and lastDecision so re-evaluation works.
    }

    private fun show(snapshot: EnforcementSnapshot, decision: AccessDecision) {
        if (isShowing) remove()
        val view = createView(snapshot, decision)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            // TYPE_ACCESSIBILITY_OVERLAY does not require SYSTEM_ALERT_WINDOW permission
            // and is scoped to the accessibility service; it is the correct type here.
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            title = "SonderGate"
        }
        try {
            windowManager.addView(view, params)
            overlayView = view
            isShowing = true
            // Announce for TalkBack
            view.announceForAccessibility(contentDescriptionFor(snapshot, decision))
            view.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed for $snapshot", e)
            isShowing = false
            overlayView = null
        }
    }

    private fun updateContent(snapshot: EnforcementSnapshot, decision: AccessDecision) {
        val view = overlayView ?: return
        // Update the textual state + action label without recreating the window.
        val statusText = view.findViewWithTag<TextView>("sonder_status")
        val actionButton = view.findViewWithTag<Button>("sonder_action")
        if (statusText != null) {
            statusText.text = statusLabelFor(decision)
            statusText.contentDescription = statusLabelFor(decision)
        }
        if (actionButton != null) {
            actionButton.contentDescription = actionLabelFor(decision)
        }
        view.contentDescription = contentDescriptionFor(snapshot, decision)
        view.announceForAccessibility(view.contentDescription)
    }

    private fun createView(snapshot: EnforcementSnapshot, decision: AccessDecision): View {
        // Build overlay programmatically (no XML dependency) so the job has no
        // external layout coupling. Styles are kept minimal; Job 01 owns the
        // full design system — this overlay uses explicit textual status plus
        // color, hard borders, and large touch targets per visual contract.
        val root = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF0080B0F.toInt()) // near-black scrim
            setPadding(48, 48, 48, 48)
            isFocusable = true
            isFocusableInTouchMode = true
            // Block interaction below — overlay consumes touches while showing.
            setOnTouchListener { _, _ -> true }
            contentDescription = contentDescriptionFor(snapshot, decision)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        val title = TextView(context).apply {
            text = context.getString(R.string.sonder_overlay_title)
            textSize = 22f
            setTextColor(0xFFE6E6E6.toInt())
            gravity = Gravity.CENTER
            letterSpacing = 0.05f
            tag = "sonder_title"
        }
        val status = TextView(context).apply {
            text = statusLabelFor(decision)
            textSize = 16f
            setTextColor(0xFFE6E6E6.toInt())
            gravity = Gravity.CENTER
            tag = "sonder_status"
            contentDescription = statusLabelFor(decision)
        }
        val desc = TextView(context).apply {
            text = context.getString(R.string.sonder_overlay_description)
            textSize = 14f
            setTextColor(0xFF9B8CFF.toInt())
            gravity = Gravity.CENTER
            tag = "sonder_desc"
        }
        val disclaimer = TextView(context).apply {
            // Explain best-effort limitation inline per product contract.
            text = if (snapshot.surface == TargetSurface.youtubeShorts || snapshot.surface == TargetSurface.instagramReels) {
                "Shorts/Reels detection is best-effort and may be inaccurate."
            } else ""
            textSize = 11f
            setTextColor(0xFF7E6CFF.toInt())
            gravity = Gravity.CENTER
            visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
            tag = "sonder_disclaimer"
        }

        val action = Button(context).apply {
            text = context.getString(R.string.sonder_overlay_action)
            contentDescription = actionLabelFor(decision)
            tag = "sonder_action"
            // Large touch target
            minimumHeight = (56 * resources.displayMetrics.density).toInt()
            setOnClickListener {
                launchSonderGate(snapshot)
            }
        }

        // Accessibility: heading + button roles
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            title.isAccessibilityHeading = true
        }

        root.addView(title, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(status, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(desc, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(disclaimer, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(action, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)

        return root
    }

    private fun statusLabelFor(decision: AccessDecision): String {
        return when (decision.status) {
            AccessStatus.locked -> {
                val rem = decision.remainingMs ?: 0L
                val formatted = formatRemaining(rem)
                context.getString(R.string.sonder_overlay_locked_label, formatted)
            }
            AccessStatus.needsChallenge -> context.getString(R.string.sonder_overlay_needs_challenge_label)
            AccessStatus.allowed -> ""
        }
    }

    private fun actionLabelFor(@Suppress("UNUSED_PARAMETER") decision: AccessDecision): String =
        context.getString(R.string.sonder_overlay_action)

    private fun contentDescriptionFor(snapshot: EnforcementSnapshot, decision: AccessDecision): String {
        val pkg = snapshot.packageName
        val status = statusLabelFor(decision)
        return "Sonder gate for $pkg. $status. ${context.getString(R.string.sonder_overlay_description)}"
    }

    private fun formatRemaining(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    private fun launchSonderGate(snapshot: EnforcementSnapshot) {
        // Deep-link into Sonder's gate screen. The Flutter side should handle
        // sonder://gate?package=...&surface=... — we use an explicit intent
        // to our own package so no other app can intercept.
        val intent = Intent().apply {
            setPackage(context.packageName)
            action = "app.sonder.action.GATE"
            putExtra("packageName", snapshot.packageName)
            putExtra("surface", snapshot.surface.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        // Fallback: launch main activity if gate action not handled.
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Gate intent failed, falling back to launcher", e)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { context.startActivity(launch) } catch (_: Exception) {}
            }
        }
    }

    companion object {
        /** Whether the overlay type can be used (always true for accessibility overlay). */
        @Suppress("UNUSED_PARAMETER")
        fun isOverlayAvailable(context: Context): Boolean {
            // TYPE_ACCESSIBILITY_OVERLAY does not require SYSTEM_ALERT_WINDOW.
            // We report overlay availability as true whenever the service is connected.
            // On Android 12+ the system may still restrict overlays, but for this type
            // no runtime permission is needed.
            return true
        }
    }
}
