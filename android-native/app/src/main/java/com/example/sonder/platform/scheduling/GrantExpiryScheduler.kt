package com.example.sonder.platform.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sets an inexact alarm at grant end; [ExpiryReceiver] then revokes the grant and
 * notifies. Inexact on purpose — no SCHEDULE_EXACT_ALARM permission needed, and
 * second-level precision is irrelevant for a 5-minute window.
 */
@Singleton
class GrantExpiryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleExpiry(packageName: String, endAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            Intent(context, ExpiryReceiver::class.java).apply {
                putExtra(ExpiryReceiver.EXTRA_PACKAGE, packageName)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAtMillis, pi)
    }
}
