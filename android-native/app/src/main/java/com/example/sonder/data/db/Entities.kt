package com.example.sonder.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Apps the user has chosen to gate behind blackjack. */
@Entity(tableName = "targets")
data class TargetEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val enabled: Boolean = true,
    val createdAtMillis: Long,
)

/** One active access grant per package; absolute epoch end time. */
@Entity(tableName = "grants")
data class GrantEntity(
    @PrimaryKey val packageName: String,
    val endAtMillis: Long,
    /** Last time the accessibility service saw this package in the foreground. */
    val lastSeenMillis: Long,
    val revokedReason: String? = null,
)

/** Accumulated lockout debt per package (capped at 60 min by the policy). */
@Entity(tableName = "debt")
data class DebtEntity(
    @PrimaryKey val packageName: String,
    val debtMillis: Long,
)

/** Active lockout window per package (serving debt). */
@Entity(tableName = "lockouts")
data class LockoutEntity(
    @PrimaryKey val packageName: String,
    val untilMillis: Long,
)

/** One blackjack hand per record — powers the Stats screen. */
@Entity(tableName = "hands")
data class HandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val outcome: String, // WIN, LOSE, PUSH
    val debtAfterMillis: Long,
    val playedAtMillis: Long,
)
