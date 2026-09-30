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
 *
 * One alarm per package, keyed by `packageName.hashCode()`, so earning a second grant
 * replaces the first alarm rather than queueing two. The end time rides in the intent as
 * well, so the receiver can tell an alarm that is still current from one left over from a
 * grant that has since been replaced.
 */
@Singleton
class GrantExpiryScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleExpiry(packageName: String, endAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            endAtMillis,
            pendingIntent(packageName, endAtMillis, PendingIntent.FLAG_UPDATE_CURRENT),
        )
    }

    /**
     * Drop the alarm for [packageName], because the grant it was set for is already gone —
     * revoked for an absence, or released. Without this the alarm still fires and wakes the
     * process to announce an expiry that happened minutes earlier.
     */
    fun cancelExpiry(packageName: String) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // FLAG_NO_CREATE: a cancel must never be the thing that brings a PendingIntent into
        // existence, or cancelling a grant with no alarm would leave one behind.
        val pi = PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            intentFor(packageName, endAtMillis = 0L),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    /**
     * Extras are not part of a PendingIntent's identity, so the end time does not affect
     * which alarm this resolves to — it only tells the receiver what it was set for.
     */
    private fun intentFor(packageName: String, endAtMillis: Long) =
        Intent(context, ExpiryReceiver::class.java).apply {
            putExtra(ExpiryReceiver.EXTRA_PACKAGE, packageName)
            putExtra(ExpiryReceiver.EXTRA_END_AT, endAtMillis)
        }

    private fun pendingIntent(packageName: String, endAtMillis: Long, flags: Int) =
        PendingIntent.getBroadcast(
            context,
            packageName.hashCode(),
            intentFor(packageName, endAtMillis),
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
}
