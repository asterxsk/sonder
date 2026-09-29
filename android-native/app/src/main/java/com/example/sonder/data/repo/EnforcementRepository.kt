package com.example.sonder.data.repo

import com.example.sonder.data.db.DailyUsageDao
import com.example.sonder.data.db.DailyUsageEntity
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
import com.example.sonder.domain.AccessRules
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.LockoutSnapshot
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.map
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
    private val dailyUsageDao: DailyUsageDao,
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

    /**
     * The raw per-app overrides as stored: a null field inherits the AccessPolicy default.
     * Unlike [AccessRules], nothing is coalesced, so a screen can tell a chosen value that
     * happens to equal the default from an inherited one.
     *
     * dailyCapMillis breaks that pattern: null there means unlimited, not inherit, so it is
     * always a chosen state rather than an absence of choice.
     */
    data class TargetOverrides(
        val winGrantMillis: Long? = null,
        val lossDebtMillis: Long? = null,
        val maxDebtMillis: Long? = null,
        val absenceRevokeMillis: Long? = null,
        val dailyCapMillis: Long? = null,
    )

    /** Effective per-app policy: row overrides, else the AccessPolicy defaults. */
    suspend fun rulesFor(packageName: String): AccessRules =
        targetDao.get(packageName).toRules()

    /** Raw per-app overrides as stored, or an all-null (fully inherited) set when no row. */
    suspend fun overridesFor(packageName: String): TargetOverrides =
        targetDao.get(packageName).toOverrides()

    /** Effective per-app policy, re-emitted whenever the target row changes. */
    fun observeRules(packageName: String): Flow<AccessRules> =
        targetDao.observeAll().map { targets -> targets.find { it.packageName == packageName }.toRules() }

    /** Raw per-app overrides, re-emitted whenever the target row changes; null when no row. */
    fun observeOverrides(packageName: String): Flow<TargetOverrides?> =
        targetDao.observeAll().map { targets ->
            targets.find { it.packageName == packageName }?.toOverrides()
        }

    /**
     * Persist the raw per-app overrides. A null field is written as null, so a knob the
     * user never touched keeps inheriting the AccessPolicy default — the CUSTOM/DEFAULT
     * label reads that absence and would otherwise flip every untouched knob to CUSTOM
     * the moment any one knob is edited. The target's label, enabled flag and creation
     * time are left untouched.
     *
     * The settings screen is reachable from the ALL tab, where every launchable app has
     * a row — including apps never enabled as targets, which have no stored row yet. A
     * missing row therefore materialises here (disabled, so nothing becomes gated) rather
     * than dropping the write, which is what "tapping a preset persists it" requires.
     */
    suspend fun updateOverrides(packageName: String, overrides: TargetOverrides) {
        // Materialise first, then name the five columns this call owns. Writing the whole
        // row would mean writing the label and the enabled flag too, from a value read
        // before this function started — so an ADD committed in the same instant would be
        // undone here, or an override saved in the same instant undone there.
        targetDao.insertIfAbsent(
            TargetEntity(
                packageName = packageName,
                label = packageName,
                enabled = false,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        targetDao.setOverrides(
            pkg = packageName,
            winGrant = overrides.winGrantMillis,
            lossDebt = overrides.lossDebtMillis,
            maxDebt = overrides.maxDebtMillis,
            absenceRevoke = overrides.absenceRevokeMillis,
            dailyCap = overrides.dailyCapMillis,
        )
    }

    /** Access already granted to [packageName] on the local day containing [nowMillis]. */
    suspend fun dailyGrantedMillis(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val usage = dailyUsageDao.get(packageName) ?: return 0L
        return if (usage.epochDay == epochDay(nowMillis)) usage.grantedMillis else 0L
    }

    /** Today's granted millis for the package, resetting when the local day rolls over. */
    fun observeDailyGrantedMillis(packageName: String): Flow<Long> =
        dailyUsageDao.observe(packageName).map { usage ->
            if (usage != null && usage.epochDay == epochDay(System.currentTimeMillis())) {
                usage.grantedMillis
            } else {
                0L
            }
        }

    /**
     * Why the package is locked out: "DEBT" or "DAILY_CAP"; null when not locked.
     * A row whose untilMillis is in the past is already over even if purgeExpired has not
     * swept it yet, so it reports null rather than a stale reason. Rows written before
     * reasons existed carry reason == null but can only ever have been debt lockouts, so
     * a null reason on a live row reads as DEBT.
     */
    suspend fun lockoutReason(packageName: String): String? =
        lockoutDao.get(packageName)
            ?.takeIf { it.untilMillis > System.currentTimeMillis() }
            ?.let { it.reason ?: LOCKOUT_REASON_DEBT }

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
        val rules = rulesFor(packageName)
        val grantedToday = dailyGrantedMillis(packageName, nowMillis)
        val result = AccessPolicy.onHandResult(outcome, debtBefore, nowMillis, rules, grantedToday)

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
            // Fresh access: clear any stale lockout, write the grant, record the day's usage.
            lockoutDao.clear(packageName)
            grantDao.upsert(
                GrantEntity(
                    packageName = packageName,
                    endAtMillis = result.grantedUntil,
                    lastSeenMillis = nowMillis,
                ),
            )
            recordGrantedUsage(packageName, nowMillis, result.grantedUntil - nowMillis)
        } else if (outcome == HandOutcome.LOSE) {
            // Walking away after a loss means serving the full remaining debt.
            val lockoutUntil = AccessPolicy.lockoutUntil(result.debtMillis, nowMillis)
            lockoutDao.upsert(LockoutEntity(packageName, lockoutUntil, reason = LOCKOUT_REASON_DEBT))
        }

        if (result.capLockoutUntil != null) {
            // The day's allowance is spent: locked until the next local midnight.
            lockoutDao.upsert(LockoutEntity(packageName, result.capLockoutUntil, reason = LOCKOUT_REASON_DAILY_CAP))
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
        if (AccessPolicy.shouldRevokeForAbsence(snapshot, nowMillis, rulesFor(packageName))) {
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

    /** Add [grantedMillis] to the package's tally for the local day containing [nowMillis]. */
    private suspend fun recordGrantedUsage(packageName: String, nowMillis: Long, grantedMillis: Long) {
        val today = epochDay(nowMillis)
        val existing = dailyUsageDao.get(packageName)
        val priorMillis = if (existing != null && existing.epochDay == today) existing.grantedMillis else 0L
        dailyUsageDao.upsert(DailyUsageEntity(packageName, today, priorMillis + grantedMillis))
    }

    private fun TargetEntity?.toOverrides(): TargetOverrides = TargetOverrides(
        winGrantMillis = this?.winGrantMillis,
        lossDebtMillis = this?.lossDebtMillis,
        maxDebtMillis = this?.maxDebtMillis,
        absenceRevokeMillis = this?.absenceRevokeMillis,
        dailyCapMillis = this?.dailyCapMillis,
    )

    private fun TargetEntity?.toRules(): AccessRules = AccessRules(
        winGrantMillis = this?.winGrantMillis ?: AccessPolicy.WIN_GRANT_MILLIS,
        lossDebtMillis = this?.lossDebtMillis ?: AccessPolicy.LOSS_DEBT_MILLIS,
        maxDebtMillis = this?.maxDebtMillis ?: AccessPolicy.MAX_DEBT_MILLIS,
        absenceRevokeMillis = this?.absenceRevokeMillis ?: AccessPolicy.ABSENCE_REVOKE_MILLIS,
        dailyCapMillis = this?.dailyCapMillis,
    )

    private fun epochDay(nowMillis: Long): Long =
        Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun GrantEntity.toSnapshot() = GrantSnapshot(packageName, endAtMillis, lastSeenMillis)
    private fun LockoutEntity.toSnapshot() = LockoutSnapshot(packageName, untilMillis, debtMillis = 0L)

    companion object {
        /** LockoutEntity.reason written when the user is serving accumulated debt. */
        const val LOCKOUT_REASON_DEBT = "DEBT"

        /** LockoutEntity.reason written when the daily cap is spent. */
        const val LOCKOUT_REASON_DAILY_CAP = "DAILY_CAP"
    }
}
