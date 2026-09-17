package com.example.sonder.data.repo

import com.example.sonder.data.db.DebtDao
import com.example.sonder.data.db.DebtEntity
import com.example.sonder.data.db.GrantDao
import com.example.sonder.data.db.GrantEntity
import com.example.sonder.data.db.HandDao
import com.example.sonder.data.db.HandEntity
import com.example.sonder.data.db.LockoutDao
import com.example.sonder.data.db.LockoutEntity
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.db.TargetEntity
import com.example.sonder.di.ApplicationScope
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.LockoutSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Central enforcement coordinator: the single place that mutates grants, debt,
 * lockouts and hand history. Domain policy stays pure; this class persists results.
 *
 * Keeps a warm in-memory snapshot of the enforcement state (targets + grants +
 * lockouts, refreshed on every mutation) so the accessibility hot path can make
 * its decision without Room round-trips. Room remains the source of truth; this
 * cache is only a read-through accelerator and is rebuilt from flows on start.
 */
@Singleton
class EnforcementRepository @Inject constructor(
    private val targetDao: TargetDao,
    private val grantDao: GrantDao,
    private val debtDao: DebtDao,
    private val lockoutDao: LockoutDao,
    private val handDao: HandDao,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val _targets = MutableStateFlow<Map<String, TargetEntity>>(emptyMap())
    private val _grants = MutableStateFlow<Map<String, GrantEntity>>(emptyMap())
    private val _lockouts = MutableStateFlow<Map<String, LockoutEntity>>(emptyMap())
    private var cacheStarted = false

    /** Rebuild the cache from Room flows; idempotent, cheap after the first call. */
    fun start() {
        if (cacheStarted) return
        cacheStarted = true
        targetDao.observeAll()
            .onEach { rows -> _targets.value = rows.associateBy { it.packageName } }
            .launchIn(externalScope)
        grantDao.observeAll()
            .onEach { rows -> _grants.value = rows.associateBy { it.packageName } }
            .launchIn(externalScope)
        lockoutDao.observeAll()
            .onEach { rows -> _lockouts.value = rows.associateBy { it.packageName } }
            .launchIn(externalScope)
    }

    fun enabledTarget(pkg: String): TargetEntity? = _targets.value[pkg]?.takeIf { it.enabled }

    fun cachedGrant(pkg: String): GrantEntity? = _grants.value[pkg]

    fun cachedLockout(pkg: String): LockoutEntity? = _lockouts.value[pkg]

    /** Observe the enforcement state of one package. */
    fun observeState(packageName: String): Flow<EnforcementState> =
        combine(
            targetDao.observeAll(),
            grantDao.observeAll(),
            lockoutDao.observeAll(),
        ) { targets, grants, lockouts ->
            val enabled = targets.find { it.packageName == packageName }?.enabled ?: false
            AccessPolicy.stateFor(
                packageName = packageName,
                enabled = enabled,
                grant = grants.find { it.packageName == packageName }?.toSnapshot(),
                lockout = lockouts.find { it.packageName == packageName }?.toSnapshot(),
                nowMillis = System.currentTimeMillis(),
            )
        }

    suspend fun currentDebt(packageName: String): Long =
        debtDao.get(packageName)?.debtMillis ?: 0L

    /** Is this package an enabled target? */
    suspend fun isTargetEnabled(packageName: String): Boolean =
        targetDao.get(packageName)?.enabled == true

    /** Is there a grant for the package that is still valid right now? */
    suspend fun hasActiveGrant(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val grant = grantDao.get(packageName) ?: return false
        val snapshot = grant.toSnapshot()
        if (!AccessPolicy.isGrantActive(snapshot, nowMillis)) {
            grantDao.delete(packageName)
            return false
        }
        return true
    }

    /** Remaining grant time in millis, or 0 when none active. */
    suspend fun grantRemainingMillis(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val grant = grantDao.get(packageName)?.toSnapshot() ?: return 0L
        return if (AccessPolicy.isGrantActive(grant, nowMillis)) grant.endAtMillis - nowMillis else 0L
    }

    /** Remaining lockout time in millis, or 0 when none active. */
    suspend fun lockoutRemainingMillis(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val lockout = lockoutDao.get(packageName) ?: return 0L
        return (lockout.untilMillis - nowMillis).coerceAtLeast(0L)
    }

    /** Can the user open the table right now (not locked out)? */
    suspend fun canPlay(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean =
        AccessPolicy.canPlay(lockoutDao.get(packageName)?.toSnapshot(), nowMillis)

    /**
     * Record a finished blackjack hand and apply the policy result.
     * Returns the granted-until millis when access was granted (null otherwise).
     */
    suspend fun onHandResult(
        packageName: String,
        outcome: HandOutcome,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val debtBefore = currentDebt(packageName)
        val result = AccessPolicy.onHandResult(outcome, debtBefore, nowMillis)

        debtDao.upsert(DebtEntity(packageName, result.debtMillis))
        handDao.insert(
            HandEntity(
                packageName = packageName,
                outcome = outcome.name,
                debtAfterMillis = result.debtMillis,
                playedAtMillis = nowMillis,
            ),
        )

        if (result.grantedUntil != null) {
            // Fresh access: clear any stale lockout, write the grant.
            lockoutDao.clear(packageName)
            grantDao.upsert(
                GrantEntity(
                    packageName = packageName,
                    endAtMillis = result.grantedUntil,
                    lastSeenMillis = nowMillis,
                ),
            )
        } else if (outcome == HandOutcome.LOSE) {
            // Walking away after a loss means serving the full remaining debt.
            val lockoutUntil = AccessPolicy.lockoutUntil(result.debtMillis, nowMillis)
            lockoutDao.upsert(LockoutEntity(packageName, lockoutUntil))
        }
        return result.grantedUntil
    }

    /** The accessibility service reports the user is looking at the package right now. */
    suspend fun recordLastSeen(packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        grantDao.touchLastSeen(packageName, nowMillis)
    }

    /**
     * Evaluate absence-based revocation for an active grant. Called on every
     * foreground window event for a granted package.
     */
    suspend fun evaluateAbsence(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val grant = grantDao.get(packageName) ?: return false
        val snapshot = grant.toSnapshot()
        if (!AccessPolicy.isGrantActive(snapshot, nowMillis)) {
            grantDao.delete(packageName)
            return false
        }
        if (AccessPolicy.shouldRevokeForAbsence(snapshot, nowMillis)) {
            grantDao.delete(packageName)
            return true
        }
        return false
    }

    /** Revoke a grant immediately (e.g. user action or expiry sweep). */
    suspend fun revokeGrant(packageName: String, reason: String) {
        grantDao.delete(packageName)
    }

    /** Housekeeping: purge expired grants/lockouts (called on boot and periodically). */
    suspend fun purgeExpired(nowMillis: Long = System.currentTimeMillis()) {
        grantDao.purgeExpired(nowMillis)
        lockoutDao.purgeExpired(nowMillis)
    }

    private fun GrantEntity.toSnapshot() = GrantSnapshot(packageName, endAtMillis, lastSeenMillis)
    private fun LockoutEntity.toSnapshot() = LockoutSnapshot(packageName, untilMillis, debtMillis = 0L)
}
