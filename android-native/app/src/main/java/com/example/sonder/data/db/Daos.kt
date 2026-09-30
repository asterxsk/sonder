package com.example.sonder.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TargetDao {
    @Query("SELECT * FROM targets ORDER BY label")
    fun observeAll(): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE enabled = 1")
    fun observeEnabled(): Flow<List<TargetEntity>>

    @Query("SELECT * FROM targets WHERE packageName = :pkg")
    suspend fun get(pkg: String): TargetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(target: TargetEntity)

    /**
     * Materialises a row for [target] and leaves an existing one exactly as it is. The
     * picker and the settings screen both mean "there should be a row for this package,
     * keeping whatever is already there" — REPLACE would answer that with a whole-row
     * write built from a value read a moment earlier, silently reverting a concurrent
     * edit to any column the caller did not mean to touch.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(target: TargetEntity)

    /** Enables [pkg] under [label], naming only those two columns. */
    @Query("UPDATE targets SET enabled = 1, label = :label WHERE packageName = :pkg")
    suspend fun enable(pkg: String, label: String)

    /** Writes the per-app overrides and nothing else, so label and enabled survive. */
    @Query(
        "UPDATE targets SET winGrantMillis = :winGrant, lossDebtMillis = :lossDebt, " +
            "maxDebtMillis = :maxDebt, absenceRevokeMillis = :absenceRevoke, " +
            "dailyCapMillis = :dailyCap WHERE packageName = :pkg",
    )
    suspend fun setOverrides(
        pkg: String,
        winGrant: Long?,
        lossDebt: Long?,
        maxDebt: Long?,
        absenceRevoke: Long?,
        dailyCap: Long?,
    )

    @Query("UPDATE targets SET enabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)

    /**
     * Set how much of [pkg] is gated, naming only that column.
     *
     * A separate statement rather than a field on [setOverrides], for the same reason that
     * one exists: the scope is chosen on its own control, and a whole-row write built from a
     * value read a moment earlier would revert a concurrent edit to a knob this call does
     * not own. Writes the stored form — see [com.example.sonder.domain.BlockScope.stored].
     */
    @Query("UPDATE targets SET blockScope = :scope WHERE packageName = :pkg")
    suspend fun setBlockScope(pkg: String, scope: String)

    @Query("DELETE FROM targets WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

@Dao
interface GrantDao {
    @Query("SELECT * FROM grants WHERE packageName = :pkg")
    suspend fun get(pkg: String): GrantEntity?

    @Query("SELECT * FROM grants")
    fun observeAll(): Flow<List<GrantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(grant: GrantEntity)

    @Query("UPDATE grants SET lastSeenMillis = :seenMillis WHERE packageName = :pkg")
    suspend fun touchLastSeen(pkg: String, seenMillis: Long)

    @Query("DELETE FROM grants WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("DELETE FROM grants WHERE endAtMillis <= :nowMillis")
    suspend fun purgeExpired(nowMillis: Long)

    /** Grants still running, for whoever has to re-arm their expiry alarms after a reboot. */
    @Query("SELECT * FROM grants WHERE endAtMillis > :nowMillis")
    suspend fun liveGrants(nowMillis: Long): List<GrantEntity>
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt WHERE packageName = :pkg")
    suspend fun get(pkg: String): DebtEntity?

    @Query("SELECT * FROM debt")
    fun observeAll(): Flow<List<DebtEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(debt: DebtEntity)

    @Query("DELETE FROM debt WHERE packageName = :pkg")
    suspend fun clear(pkg: String)
}

@Dao
interface LockoutDao {
    @Query("SELECT * FROM lockouts WHERE packageName = :pkg")
    suspend fun get(pkg: String): LockoutEntity?

    @Query("SELECT * FROM lockouts")
    fun observeAll(): Flow<List<LockoutEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lockout: LockoutEntity)

    @Query("DELETE FROM lockouts WHERE packageName = :pkg")
    suspend fun clear(pkg: String)

    @Query("DELETE FROM lockouts WHERE untilMillis <= :nowMillis")
    suspend fun purgeExpired(nowMillis: Long)

    /**
     * The debt lockouts that have run out, read before [purgeExpired] deletes them:
     * an expired debt lockout is a debt that has been served, and the debt row it was
     * serving has to be cleared with it. Rows written before reasons existed carry no
     * reason but can only ever have been debt lockouts, so they match too.
     */
    @Query(
        "SELECT * FROM lockouts WHERE untilMillis <= :nowMillis " +
            "AND (reason = :reason OR reason IS NULL)",
    )
    suspend fun expired(nowMillis: Long, reason: String): List<LockoutEntity>
}

/** Win/loss tally for the Stats screen, from one scan rather than two. */
data class HandTally(val wins: Int, val losses: Int)

@Dao
interface HandDao {
    @Insert
    suspend fun insert(hand: HandEntity)

    @Query("SELECT * FROM hands ORDER BY playedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<HandEntity>>

    /**
     * Both counts in one pass. Two separate `COUNT(*)` flows over the same table meant two
     * scans and two emissions for every hand played.
     */
    @Query(
        "SELECT COALESCE(SUM(outcome = 'WIN'), 0) AS wins, " +
            "COALESCE(SUM(outcome = 'LOSE'), 0) AS losses FROM hands",
    )
    fun observeTally(): Flow<HandTally>

    /**
     * Drop everything but the newest [keep] hands. The history only ever powers a 50-row
     * page and two counters, so it is a bounded log, not an archive: without this the table
     * and every scan over it grow for the life of the install.
     */
    @Query(
        "DELETE FROM hands WHERE id NOT IN " +
            "(SELECT id FROM hands ORDER BY playedAtMillis DESC LIMIT :keep)",
    )
    suspend fun trimTo(keep: Int)
}

@Dao
interface DailyUsageDao {
    @Query("SELECT * FROM daily_usage WHERE packageName = :pkg")
    suspend fun get(pkg: String): DailyUsageEntity?

    @Query("SELECT * FROM daily_usage WHERE packageName = :pkg")
    fun observe(pkg: String): Flow<DailyUsageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: DailyUsageEntity)

    /**
     * Add [delta] to a row that is already the [epochDay]'s tally. Returns the number of
     * rows changed: 0 means the row is missing or belongs to an earlier day, and the caller
     * must start the day instead. This is the atomic half of the day's tally — a read of the
     * stored value followed by a REPLACE loses one of two concurrent grants.
     */
    @Query(
        "UPDATE daily_usage SET grantedMillis = grantedMillis + :delta " +
            "WHERE packageName = :pkg AND epochDay = :epochDay",
    )
    suspend fun addToDay(pkg: String, epochDay: Long, delta: Long): Int

    /** Start (or restart) the day's tally at [delta], replacing any earlier day's row. */
    @Query(
        "INSERT OR REPLACE INTO daily_usage (packageName, epochDay, grantedMillis) " +
            "VALUES (:pkg, :epochDay, :delta)",
    )
    suspend fun startDay(pkg: String, epochDay: Long, delta: Long)
}
