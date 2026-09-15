package com.example.sonder.platform.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.example.sonder.R

/** Notification plumbing: enforcement channel + "access expired" heads-up. */
object Notifications {
    const val CHANNEL_ENFORCEMENT = "enforcement"

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ENFORCEMENT,
                "Enforcement",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Access windows and lockouts"
            },
        )
    }

    fun notifyAccessExpired(context: Context, packageName: String) {
        ensureChannels(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val label = try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0),
            )
        } catch (_: Exception) {
            packageName
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ENFORCEMENT)
            .setSmallIcon(R.drawable.ic_stat_sonder)
            .setContentTitle("◔ Time's up")
            .setContentText("Access to $label has expired.")
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(packageName.hashCode(), notification)
        } catch (_: SecurityException) {
            // Notification permission revoked — nothing to do.
        }
    }
}
