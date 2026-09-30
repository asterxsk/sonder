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
    /**
     * Most unspent access the app's bank may hold at once. NOT NULL and defaulted rather
     * than nullable, because there is no "inherit" left to express: with the debt model gone
     * this is the only per-app rule there is, and one hour is the default the user was
     * promised. `MIGRATION_4_5` writes the same default, so the schema Room expects and the
     * rows on disk agree.
     */
    val maxMillis: Long = DEFAULT_MAX_MILLIS,
    /**
     * "WHOLE_APP" | "SHORTS_ONLY" — see [com.example.sonder.domain.BlockScope].
     *
     * Not nullable, and defaulted rather than absent, because there is no third state to
     * express: a target either gates the whole app or only its short-form surface, and
     * every row that predates this column meant the former. `MIGRATION_2_3` writes the same
     * default, so the schema Room expects and the rows on disk agree.
     */
    val blockScope: String = "WHOLE_APP",
) {
    companion object {
        /** One hour, matching `AccessPolicy.DEFAULT_MAX_MILLIS`. */
        const val DEFAULT_MAX_MILLIS = 60 * 60_000L
    }
}

/**
 * One target's access bank: the whole of what used to be four tables.
 *
 * This replaces grants, debt, lockouts and the daily tally, because they were four
 * descriptions of one thing — how much time the user has in this app. A row here is created
 * by the first hand played and lives on across days; `epochDay` is what makes it a *daily*
 * allowance without a second table, since a bank from an earlier day reads as empty.
 */
@Entity(tableName = "time_bank")
data class TimeBankEntity(
    @PrimaryKey val packageName: String,
    /** Unspent access. Zero means out of time, which is what the gate is. */
    val remainingMillis: Long,
    /** Local day this bank belongs to (`LocalDate.toEpochDay()`); a later day reads as 0. */
    val epochDay: Long,
    /**
     * Last moment this app was billed in the foreground.
     *
     * Elapsed is `now - lastSeen`, so this is both the drain's clock and the answer to "was
     * the user actually here" — a stamp the coordinator refreshes at most every few seconds
     * while the app is on screen, and never while it is not.
     */
    val lastSeenMillis: Long,
    /**
     * When the bank last reached zero, or 0 when it has not since it was last refilled.
     *
     * Removal of the target is refused for
     * [com.example.sonder.domain.AccessPolicy.REMOVAL_LOCK_MILLIS] after this, so a bad
     * afternoon cannot be undone by deleting the app from the list a minute later.
     */
    val emptySinceMillis: Long = 0L,
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
    /** What the stake was, so the row can say what the hand was worth. */
    val stakeMillis: Long,
    /** The bank the hand left behind; 0 for every hand played under the debt model. */
    val bankAfterMillis: Long,
    val playedAtMillis: Long,
    val label: String = "",
    val playerCards: String = "",
    val dealerCards: String = "",
)
