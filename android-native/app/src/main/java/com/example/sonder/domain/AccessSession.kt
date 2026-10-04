package com.example.sonder.domain

data class AccessSession(
    val packageName: String,
    val remainingMillis: Long,
    val awaySinceMillis: Long? = null,
) {
    fun isAvailableFor(packageName: String, nowMillis: Long): Boolean =
        this.packageName == packageName &&
            remainingMillis > 0L &&
            (awaySinceMillis == null || nowMillis - awaySinceMillis < ABSENCE_GRACE_MILLIS)

    fun leave(nowMillis: Long): AccessSession =
        if (awaySinceMillis == null) copy(awaySinceMillis = nowMillis) else this

    fun resume(): AccessSession = copy(awaySinceMillis = null)

    fun consume(usedMillis: Long): AccessSession =
        copy(remainingMillis = (remainingMillis - usedMillis.coerceAtLeast(0L)).coerceAtLeast(0L))

    companion object {
        const val ABSENCE_GRACE_MILLIS = 60_000L
    }
}
