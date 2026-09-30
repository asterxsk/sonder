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

    /**
     * Writes the bank ceiling and nothing else, so label, enabled and scope survive.
     *
     * One column, named, for the same reason the scope has its own statement: a whole-row
     * write built from a value read a moment earlier would revert a concurrent edit to a
     * field this call does not own.
     */
    @Query("UPDATE targets SET maxMillis = :maxMillis WHERE packageName = :pkg")
    suspend fun setMax(pkg: String, maxMillis: Long)

    @Query("UPDATE targets SET enabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)

    /**
     * Set how much of [pkg] is gated, naming only that column.
     *
     * A separate statement rather than a field on [setMax], for the same reason that one
     * exists: the scope is chosen on its own control, and a whole-row write built from a
     * value read a moment earlier would revert a concurrent edit to the ceiling this call
     * does not own. Writes the stored form — see [com.example.sonder.domain.BlockScope.stored].
     */
    @Query("UPDATE targets SET blockScope = :scope WHERE packageName = :pkg")
    suspend fun setBlockScope(pkg: String, scope: String)

    @Query("DELETE FROM targets WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
}

/**
 * The access banks. One row per package that has ever played a hand — not one per target,
 * because the bank is what a *hand* writes and a hand can be played before the target row
 * is materialised.
 */
@Dao
interface TimeBankDao {
    @Query("SELECT * FROM time_bank WHERE packageName = :pkg")
    suspend fun get(pkg: String): TimeBankEntity?

    @Query("SELECT * FROM time_bank")
    fun observeAll(): Flow<List<TimeBankEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bank: TimeBankEntity)

    @Query("DELETE FROM time_bank WHERE packageName = :pkg")
    suspend fun delete(pkg: String)
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
