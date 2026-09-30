package com.example.sonder.platform.permissions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.sonder.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Pixel-styled popup shown when the on-open audit finds a missing permission.
 * One button per missing permission, deep-linked to the right settings page.
 *
 * The view is rebuilt from a fresh audit on every resume rather than once in
 * [onCreate]. It used to be built once, which meant that after the user went to
 * Settings and granted the thing, they came back to a dialog still listing it as
 * missing — the reason it looked like each permission had to be granted twice.
 * When the last one lands, the dialog removes itself.
 */
@AndroidEntryPoint
class PermissionPromptActivity : ComponentActivity() {

    @Inject lateinit var audit: PermissionAudit
    @Inject lateinit var settings: SettingsRepository

    /** What the current view was built from, so a no-change resume skips the rebuild. */
    private var rendered: Set<SonderPermission>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onResume() {
        super.onResume()
        // Back in charge again, so the watcher's job is done either way — without this
        // a user who returned by hand could be pulled forward again seconds later.
        PermissionHandoff.cancel()
        render()
    }

    override fun onDestroy() {
        PermissionHandoff.cancel()
        super.onDestroy()
    }

    /**
     * Read the declined-optional set, then draw. The audit itself is synchronous, but the
     * skip list is DataStore-backed, so the frame waits on one preferences read — that read
     * is what keeps a deliberately-skipped optional permission from being nagged about on
     * every launch, which is the whole point of it being optional.
     */
    private fun render() {
        lifecycleScope.launch {
            build(outstandingPermissions(audit.missingPermissions(), settings.skippedPermissionNames.first()))
        }
    }

    private fun build(missing: Set<SonderPermission>) {
        if (missing.isEmpty()) {
            finish()
            return
        }
        if (missing == rendered) return
        rendered = missing

        val dp = resources.displayMetrics.density

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xB30B0906.toInt())
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
        }

        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF15100B.toInt())
                setStroke((3 * dp).toInt(), 0xFFFFB827.toInt())
            }
        }

        frame.addView(TextView(this).apply {
            text = "▣ PERMISSIONS REQUIRED"
            setTextColor(0xFFFFB827.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.CENTER
        })

        frame.addView(TextView(this).apply {
            text = "Sonder cannot enforce your limits without these:"
            setTextColor(0xFFEAD7A1.toInt())
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 12f
            setPadding(0, (14 * dp).toInt(), 0, (14 * dp).toInt())
        })

        missing.forEach { permission ->
            frame.addView(Button(this).apply {
                text = when (permission) {
                    SonderPermission.ACCESSIBILITY -> "→  Accessibility settings"
                    SonderPermission.OVERLAY -> "→  Display over other apps"
                    SonderPermission.USAGE_ACCESS -> "→  Usage access"
                    SonderPermission.NOTIFICATIONS -> "→  Allow notifications"
                }
                // Grant it and the watcher brings Sonder forward; this dialog finishes
                // itself first so the user lands on the app, not back on this card.
                setOnClickListener {
                    PermissionHandoff.request(this@PermissionPromptActivity, permission) { finish() }
                }
            })
        }

        frame.addView(Button(this).apply {
            text = "LATER"
            setOnClickListener {
                PermissionHandoff.cancel()
                finish()
            }
        })

        root.addView(
            frame,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        setContentView(root)
    }

    companion object {
        /**
         * Shows the prompt only when [missing] still holds something worth asking about.
         *
         * Takes the set rather than the audit so the caller applies the optional-skip rule
         * first: launching a translucent activity that instantly finishes itself is a
         * flicker on the user's screen for a permission they already declined.
         */
        fun launchIfMissing(context: Context, missing: Set<SonderPermission>) {
            if (missing.isEmpty()) return
            runCatching {
                context.startActivity(
                    Intent(context, PermissionPromptActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}
