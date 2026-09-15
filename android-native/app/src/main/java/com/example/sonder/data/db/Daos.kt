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

    @Query("UPDATE targets SET enabled = :enabled WHERE packageName = :pkg")
    suspend fun setEnabled(pkg: String, enabled: Boolean)

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
}

@Dao
interface DebtDao {
    @Query("SELECT * FROM debt WHERE packageName = :pkg")
    suspend fun get(pkg: String): DebtEntity?

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
}

@Dao
interface HandDao {
    @Insert
    suspend fun insert(hand: HandEntity)

    @Query("SELECT * FROM hands ORDER BY playedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<HandEntity>>

    @Query("SELECT COUNT(*) FROM hands WHERE outcome = 'WIN'")
    fun observeWinCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM hands WHERE outcome = 'LOSE'")
    fun observeLossCount(): Flow<Int>
}
