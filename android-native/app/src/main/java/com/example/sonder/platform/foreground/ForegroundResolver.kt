package com.example.sonder.platform.foreground

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authoritative "what is actually on screen right now" lookup.
 *
 * The window-event stream is the fast path, but it is not perfectly ordered: when
 * an app launches, the launcher often emits its own window-state event *after*
 * the target app's event, which would otherwise look like "the user went Home"
 * and tear the blocker down right after it appeared.
 *
 * This resolves the real foreground package so the blocker can re-verify after
 * releasing. It reads only the foreground package name — never content — and
 * returns null when usage access is unavailable, in which case the event stream
 * alone drives enforcement.
 */
@Singleton
class ForegroundResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun currentForegroundPackage(nowMillis: Long = System.currentTimeMillis()): String? {
        val usageStats =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val events = try {
            usageStats.queryEvents(nowMillis - LOOKBACK_MILLIS, nowMillis)
        } catch (_: Throwable) {
            null
        } ?: return null

        var lastResumed: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == resumeEventType()) lastResumed = event.packageName
        }
        return lastResumed
    }

    @Suppress("DEPRECATION")
    private fun resumeEventType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    private companion object {
        const val LOOKBACK_MILLIS = 60_000L
    }
}
