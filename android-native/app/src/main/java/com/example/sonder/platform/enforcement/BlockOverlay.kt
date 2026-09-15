package com.example.sonder.platform.enforcement

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Instant-blocking overlay (SYSTEM_ALERT_WINDOW). Shows the moment a blocked app
 * reaches the foreground, before BlockActivity can launch. Pixel styling per
 * design_v3: near-black bg, amber border, pixel-ish monospace text, hard frames.
 * A visible overlay also makes the app "visible" for BAL purposes on API 29+,
 * which is what allows the gate activity launch from the background.
 */
class BlockOverlay(
    private val context: Context,
    private val targetLabel: String,
    private val lockoutRemainingMillis: Long,
    private val onTap: () -> Unit,
) {
    private var view: View? = null
    private val mainHandler = android.os.Handler(context.mainLooper)

    /**
     * Hard safety: the overlay MUST never trap the user. If the gate activity
     * hasn't taken over within this window, the overlay removes itself —
     * a stuck full-screen overlay swallows touches system-wide (including the
     * home gesture), which reads as "the whole phone is frozen".
     */
    private val autoDismissRunnable = Runnable { dismiss() }

    fun show(): Boolean = try {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.CENTER

        view = buildView().also { wm.addView(it, params) }
        mainHandler.postDelayed(autoDismissRunnable, MAX_OVERLAY_MILLIS)
        true
    } catch (_: Exception) {
        // No overlay permission or window token issue: BlockActivity launch still proceeds.
        false
    }

    fun dismiss() {
        mainHandler.removeCallbacks(autoDismissRunnable)
        try {
            view?.let { (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(it) }
        } catch (_: Exception) {
            // already removed
        }
        view = null
    }

    companion object {
        /** Overlay never outlives this. The gate normally replaces it in <1s. */
        const val MAX_OVERLAY_MILLIS = 4_000L
    }

    private fun buildView(): View {
        val dp = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xEE0B0906.toInt())
            gravity = Gravity.CENTER
            setOnClickListener { onTap() }
        }

        val frame = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(0xFF15100B.toInt())
                setStroke((3 * dp).toInt(), 0xFFA68A52.toInt())
            }
        }

        val title = TextView(context).apply {
            text = "▣  $targetLabel"
            setTextColor(0xFFEF4444.toInt())
            typeface = Typeface.MONOSPACE
            textSize = 16f
            gravity = Gravity.CENTER
        }

        val body = TextView(context).apply {
            text = if (lockoutRemainingMillis > 0) {
                "LOCKED — WAIT IT OUT"
            } else {
                "IS BLOCKED.\nWIN A HAND OF BLACKJACK\nFOR 5:00 ACCESS."
            }
            setTextColor(0xFFEAD7A1.toInt())
            typeface = Typeface.MONOSPACE
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }

        val cta = TextView(context).apply {
            text = if (lockoutRemainingMillis > 0) "⌛ SERVING TIME" else "♠  PLAY BLACKJACK →"
            setTextColor(0xFF0B0906.toInt())
            typeface = Typeface.MONOSPACE
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(0xFFFFB827.toInt())
                setStroke((2 * dp).toInt(), 0xFFC88A16.toInt())
            }
            setOnClickListener {
                // Consume tap here so it doesn't also trigger the root's onTap.
                onTap()
            }
        }

        frame.addView(title)
        frame.addView(body)
        frame.addView(cta, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (24 * dp).toInt()
        })
        root.addView(frame)
        return root
    }
}
