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
    /** Per-app overrides; null inherits the AccessPolicy default (dailyCapMillis null = unlimited). */
    val winGrantMillis: Long? = null,
    val lossDebtMillis: Long? = null,
    val maxDebtMillis: Long? = null,
    val absenceRevokeMillis: Long? = null,
    val dailyCapMillis: Long? = null,
    /**
     * "WHOLE_APP" | "SHORTS_ONLY" — see [com.example.sonder.domain.BlockScope].
     *
     * Not nullable, and defaulted rather than absent, because there is no third state to
     * express: a target either gates the whole app or only its short-form surface, and
     * every row that predates this column meant the former. `MIGRATION_2_3` writes the same
     * default, so the schema Room expects and the rows on disk agree.
     */
    val blockScope: String = "WHOLE_APP",
)

/** One active access grant per package; absolute epoch end time. */
@Entity(tableName = "grants")
data class GrantEntity(
    @PrimaryKey val packageName: String,
    val endAtMillis: Long,
    /** Last time the accessibility service saw this package in the foreground. */
    val lastSeenMillis: Long,
    /**
     * Unused. Nothing writes it — a revoked grant is deleted, so there is no row left to
     * carry a reason. Left in the schema rather than dropped, because removing a column
     * needs a table rebuild migration and it buys nothing.
     */
    val revokedReason: String? = null,
)

/** Accumulated lockout debt per package (capped at 60 min by the policy). */
@Entity(tableName = "debt")
data class DebtEntity(
    @PrimaryKey val packageName: String,
    val debtMillis: Long,
)

/** Active lockout window per package (serving debt or a spent daily cap). */
@Entity(tableName = "lockouts")
data class LockoutEntity(
    @PrimaryKey val packageName: String,
    val untilMillis: Long,
    /** "DEBT" | "DAILY_CAP"; null on rows written before reasons existed. */
    val reason: String? = null,
)

/** Access granted per package per local day, so a daily cap can be enforced. */
@Entity(tableName = "daily_usage")
data class DailyUsageEntity(
    @PrimaryKey val packageName: String,
    /** java.time.LocalDate.toEpochDay() for the day the time was granted. */
    val epochDay: Long,
    val grantedMillis: Long,
)

/**
 * One blackjack hand per record — powers the Stats screen.
 *
 * The label and the two hands are *snapshots*, not lookups: what the app was called and
 * what was on the table at the moment it was played. A history row that joined the targets
 * table instead would relabel old hands when a target is renamed, and could not show the
 * cards at all, because the gate holds them in memory and drops them when the table resets.
 *
 * Both card fields are a short human-readable line ("A♠ K♥ · 21"), empty for a hand played
 * before this was recorded — the row then simply omits them rather than inventing a hand.
 */
@Entity(tableName = "hands")
data class HandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val outcome: String, // WIN, LOSE, PUSH
    val debtAfterMillis: Long,
    val playedAtMillis: Long,
    val label: String = "",
    val playerCards: String = "",
    val dealerCards: String = "",
)
