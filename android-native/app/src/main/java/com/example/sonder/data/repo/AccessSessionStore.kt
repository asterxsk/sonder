package com.example.sonder.data.repo

import com.example.sonder.domain.AccessSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AccessEntry { DENIED, ACTIVE, RESUMED }

@Singleton
class AccessSessionStore @Inject constructor() {
    private val _sessions = MutableStateFlow<Map<String, AccessSession>>(emptyMap())
    val sessions: StateFlow<Map<String, AccessSession>> = _sessions.asStateFlow()

    @Synchronized
    fun grant(packageName: String, durationMillis: Long) {
        _sessions.value = _sessions.value + (
            packageName to AccessSession(packageName, durationMillis.coerceAtLeast(0L))
        )
    }

    @Synchronized
    fun enter(packageName: String, nowMillis: Long): AccessEntry {
        val current = _sessions.value[packageName] ?: return AccessEntry.DENIED
        if (!current.isAvailableFor(packageName, nowMillis)) {
            _sessions.value = _sessions.value - packageName
            return AccessEntry.DENIED
        }
        if (current.awaySinceMillis == null) return AccessEntry.ACTIVE
        _sessions.value = _sessions.value + (packageName to current.resume())
        return AccessEntry.RESUMED
    }

    fun isAvailableFor(packageName: String, nowMillis: Long): Boolean =
        _sessions.value[packageName]?.isAvailableFor(packageName, nowMillis) == true

    fun remainingMillis(packageName: String): Long =
        _sessions.value[packageName]?.remainingMillis ?: 0L

    @Synchronized
    fun leaveActive(nowMillis: Long, except: String? = null) {
        _sessions.value = _sessions.value.mapValues { (pkg, session) ->
            if (session.awaySinceMillis == null && pkg != except) {
                session.leave(nowMillis)
            } else {
                session
            }
        }
    }

    @Synchronized
    fun consume(packageName: String, usedMillis: Long) {
        val current = _sessions.value[packageName] ?: return
        val updated = current.consume(usedMillis)
        _sessions.value = if (updated.remainingMillis > 0L) {
            _sessions.value + (packageName to updated)
        } else {
            _sessions.value - packageName
        }
    }

    @Synchronized
    fun clear(packageName: String? = null) {
        _sessions.value = if (packageName == null) emptyMap() else _sessions.value - packageName
    }
}
