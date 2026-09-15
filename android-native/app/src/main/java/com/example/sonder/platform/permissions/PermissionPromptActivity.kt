package com.example.sonder.platform.permissions

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.sonder.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Pixel-styled popup shown when the 10-second audit finds a missing permission.
 * One button per missing permission, deep-linked to the right settings page.
 */
@AndroidEntryPoint
class PermissionPromptActivity : ComponentActivity() {

    @Inject lateinit var audit: PermissionAudit

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val missing = audit.missingPermissions()
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
                setOnClickListener { openPermissionSettings(permission) }
            })
        }

        frame.addView(Button(this).apply {
            text = "LATER"
            setOnClickListener { finish() }
        })

        root.addView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    private fun openPermissionSettings(permission: SonderPermission) {
        val intent = when (permission) {
            SonderPermission.ACCESSIBILITY ->
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    putExtra(
                        Intent.EXTRA_COMPONENT_NAME,
                        ComponentName(this@PermissionPromptActivity, "com.example.sonder.platform.accessibility.SonderAccessibilityService"),
                    )
                }
            SonderPermission.OVERLAY ->
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
            SonderPermission.USAGE_ACCESS ->
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            SonderPermission.NOTIFICATIONS ->
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
