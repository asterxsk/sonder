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
import com.example.sonder.data.db.SonderDatabase
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.db.TargetEntity
import androidx.room.withTransaction
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
    private val database: SonderDatabase,
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
    private val _debts = MutableStateFlow<Map<String, DebtEntity>>(emptyMap())

    /**
     * One bit per cache table, set when that table's flow first answers. Every table has to
     * be in before a decision may be made on the cache: an empty targets map says "not a
     * target" and an empty grants map says "no grant", and either answer taken too early is
     * wrong in the direction that lets an app through.
     *
     * Read from the accessibility dispatcher and written from the flows' own coroutines, so
     * it is atomic rather than a plain Boolean.
     */
    private val loadedTables = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var started = false

    /** True once all four cache tables have answered Room at least once. */
    fun isCacheReady(): Boolean = loadedTables.get() == ALL_TABLES_LOADED

    private fun markLoaded(table: Int) {
        loadedTables.updateAndGet { it or (1 shl table) }
    }

    /** Rebuild the cache from Room flows; idempotent, cheap after the first call. */
    fun start() {
        if (started) return
        started = true
        targetDao.observeAll()
            .onEach { rows ->
                _targets.value = rows.associateBy { it.packageName }
                markLoaded(TABLE_TARGETS)
            }
            .launchIn(externalScope)
        grantDao.observeAll()
            .onEach { rows ->
                _grants.value = rows.associateBy { it.packageName }
                markLoaded(TABLE_GRANTS)
            }
            .launchIn(externalScope)
        lockoutDao.observeAll()
            .onEach { rows ->
                _lockouts.value = rows.associateBy { it.packageName }
                markLoaded(TABLE_LOCKOUTS)
            }
            .launchIn(externalScope)
        debtDao.observeAll()
            .onEach { rows ->
                _debts.value = rows.associateBy { it.packageName }
                markLoaded(TABLE_DEBTS)
            }
            .launchIn(externalScope)
    }

    fun enabledTarget(pkg: String): TargetEntity? = _targets.value[pkg]?.takeIf { it.enabled }

    fun cachedGrant(pkg: String): GrantEntity? = _grants.value[pkg]

    fun cachedLockout(pkg: String): LockoutEntity? = _lockouts.value[pkg]

    /** Debt the package is carrying right now, from the warm cache. */
    fun cachedDebt(pkg: String): Long = _debts.value[pkg]?.debtMillis ?: 0L

    /**
     * The debt ceiling in force for the package: its override, else the global default.
     * Read from the warm cache rather than [rulesFor], because the gate decision runs on
     * the foreground hot path and must not wait on Room.
     *
     * Clamped here as well as on the way in: a row written before the clamp existed (or by a
     * future caller that bypasses [updateOverrides]) would otherwise make `isDebtAtCap` true
     * for every value and lock the app out permanently.
     */
    fun cachedMaxDebt(pkg: String): Long =
        (_targets.value[pkg]?.maxDebtMillis ?: AccessPolicy.MAX_DEBT_MILLIS)
            .coerceAtLeast(MIN_MAX_DEBT_MILLIS)

    /**
     * The absence window in force for the package: its override, else the build default.
     *
     * Cached rather than resolved through [rulesFor] for the same reason as [cachedMaxDebt]:
     * the live decision path may not wait on Room.
     */
    fun cachedAbsenceRevoke(pkg: String): Long =
        (_targets.value[pkg]?.absenceRevokeMillis ?: RuleDefaults.ABSENCE_REVOKE_MILLIS)
            .coerceAtLeast(MIN_ABSENCE_REVOKE_MILLIS)

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
        // Every override is clamped on the way in. A stored value the policy cannot work
        // with is not a preference, it is a state with no way out — see AccessRules.clamped.
        // A null field stays null: null means "inherit", and clamping it to a number would
        // silently turn every untouched knob into a chosen one.
        val clamped = AccessRules(
            winGrantMillis = overrides.winGrantMillis ?: RuleDefaults.WIN_GRANT_MILLIS,
            lossDebtMillis = overrides.lossDebtMillis ?: AccessPolicy.LOSS_DEBT_MILLIS,
            maxDebtMillis = overrides.maxDebtMillis ?: AccessPolicy.MAX_DEBT_MILLIS,
            absenceRevokeMillis = overrides.absenceRevokeMillis ?: RuleDefaults.ABSENCE_REVOKE_MILLIS,
            dailyCapMillis = overrides.dailyCapMillis,
        ).clamped()
        targetDao.setOverrides(
            pkg = packageName,
            winGrant = overrides.winGrantMillis?.let { clamped.winGrantMillis },
            lossDebt = overrides.lossDebtMillis?.let { clamped.lossDebtMillis },
            maxDebt = overrides.maxDebtMillis?.let { clamped.maxDebtMillis },
            absenceRevoke = overrides.absenceRevokeMillis?.let { clamped.absenceRevokeMillis },
            dailyCap = overrides.dailyCapMillis?.let { clamped.dailyCapMillis },
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
            debtDao.observeAll(),
        ) { targets, grants, lockouts, debts ->
            val target = targets.find { it.packageName == packageName }
            val maxDebt = (target?.maxDebtMillis ?: AccessPolicy.MAX_DEBT_MILLIS)
                .coerceAtLeast(MIN_MAX_DEBT_MILLIS)
            AccessPolicy.stateFor(
                packageName = packageName,
                enabled = target?.enabled ?: false,
                grant = grants.find { it.packageName == packageName }?.toSnapshot(),
                lockout = lockouts.find { it.packageName == packageName }?.toSnapshot(),
                nowMillis = System.currentTimeMillis(),
                // Debt at the ceiling is a lockout with no row behind it, so the state has
                // to be told about the debt too or it reports IDLE for an app the gate is
                // refusing to open.
                debtAtCap = AccessPolicy.isDebtAtCap(
                    debtMillis = debts.find { it.packageName == packageName }?.debtMillis ?: 0L,
                    maxDebtMillis = maxDebt,
                ),
            )
        }

    suspend fun currentDebt(packageName: String): Long =
        debtDao.get(packageName)?.debtMillis ?: 0L

    /**
     * Clear the debt of a package whose debt lockout has run out.
     *
     * Waiting the lockout out *is* paying the debt — the lockout was the debt written as
     * time to serve — so the debt row goes with it. Without this the debt outlives its own
     * lockout, and the app ends up gating a package whose lock timer has already expired:
     * the table sits there against a dead timer, and at the ceiling there is no way back
     * at all, because the only way to pay the debt down is to win hands and the ceiling is
     * precisely where hands stop being offered.
     *
     * A package carrying debt with no lockout row at all is *not* served: that is the
     * mid-session state, where the player is still at the table paying it down.
     *
     * @return whether anything was cleared, so a caller making a decision right now can
     *   use the cleared values instead of waiting for the cache to come round.
     */
    suspend fun serveDebtIfLockoutElapsed(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = database.withTransaction {
        val lockout = lockoutDao.get(packageName) ?: return@withTransaction false
        // A row written before reasons existed can only ever have been a debt lockout.
        if ((lockout.reason ?: LOCKOUT_REASON_DEBT) != LOCKOUT_REASON_DEBT) {
            return@withTransaction false
        }
        if (lockout.untilMillis > nowMillis) return@withTransaction false
        // Both rows or neither: a debt left behind without its lockout is the state the
        // class doc calls unserved, and at the ceiling that is an app with no way back.
        lockoutDao.clear(packageName)
        debtDao.clear(packageName)
        true
    }

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
    suspend fun canPlay(packageName: String, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val target = targetDao.get(packageName)
        val maxDebt = (target?.maxDebtMillis ?: AccessPolicy.MAX_DEBT_MILLIS)
            .coerceAtLeast(MIN_MAX_DEBT_MILLIS)
        return AccessPolicy.canPlay(
            lockout = lockoutDao.get(packageName)?.toSnapshot(),
            nowMillis = nowMillis,
            debtAtCap = AccessPolicy.isDebtAtCap(currentDebt(packageName), maxDebt),
        )
    }

    /**
     * Record a finished blackjack hand and apply the policy result.
     * Returns the granted-until millis when access was granted (null otherwise).
     *
     * One transaction: the debt row, the history row, the grant, the day's tally and the
     * lockout are one state, and a process death between any two of them leaves a shape
     * nothing describes — a grant with no tally against the cap, or a debt with no lockout
     * to serve it.
     */
    suspend fun onHandResult(
        packageName: String,
        outcome: HandOutcome,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? = database.withTransaction {
        val debtBefore = currentDebt(packageName)
        val rules = rulesFor(packageName)
        val grantedToday = dailyGrantedMillis(packageName, nowMillis)
        val result = AccessPolicy.onHandResult(outcome, debtBefore, nowMillis, rules, grantedToday)

        if (result.debtMillis > 0L) {
            debtDao.upsert(DebtEntity(packageName, result.debtMillis))
        } else {
            // Debt paid off: the row goes with it rather than standing as a zero nobody
            // reads but every observer still emits.
            debtDao.clear(packageName)
        }
        handDao.insert(
            HandEntity(
                packageName = packageName,
                outcome = outcome.name,
                debtAfterMillis = result.debtMillis,
                playedAtMillis = nowMillis,
            ),
        )
        handDao.trimTo(HAND_HISTORY_LIMIT)

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
        } else if (outcome == HandOutcome.WIN && result.debtMillis > 0L) {
            // Paying debt down with a win shortens the wait, because the lockout *is* the
            // debt written as time to serve. Without this the older, larger deadline stood:
            // the debt fell to 10 minutes and the app still waited out the 20 it was locked
            // for when the debt was 20, and the reduction the player earned was invisible.
            val lockout = lockoutDao.get(packageName)
            if (lockout != null && (lockout.reason ?: LOCKOUT_REASON_DEBT) == LOCKOUT_REASON_DEBT) {
                lockoutDao.upsert(
                    LockoutEntity(
                        packageName = packageName,
                        untilMillis = nowMillis + result.debtMillis,
                        reason = LOCKOUT_REASON_DEBT,
                    ),
                )
            }
        }

        if (result.capLockoutUntil != null) {
            // The day's allowance is spent: locked until the next local midnight.
            lockoutDao.upsert(LockoutEntity(packageName, result.capLockoutUntil, reason = LOCKOUT_REASON_DAILY_CAP))
        }
        result.grantedUntil
    }

    /** The accessibility service reports the user is looking at the package right now. */
    suspend fun recordLastSeen(packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        grantDao.touchLastSeen(packageName, nowMillis)
    }

    /** Revoke a grant immediately (e.g. user action or an absence). */
    suspend fun revokeGrant(packageName: String) {
        grantDao.delete(packageName)
    }

    /**
     * Revoke the grant for [packageName] only if it is still the one an expiry alarm was
     * set for, and report whether anything was revoked.
     *
     * Alarms do not survive a reboot and are re-armed from the grants that are live at the
     * time, so an alarm can outlive the grant it belongs to. Cutting whatever grant happens
     * to be stored when a stale alarm fires would end a session the player legitimately
     * earned; the alarm is a nudge, not an authority. [endAtMillis] of 0 means the alarm
     * carried no end time, and is treated as authoritative — that is what an alarm posted
     * by an older build looks like.
     */
    suspend fun revokeGrantIfExpiredAt(packageName: String, endAtMillis: Long): Boolean {
        val grant = grantDao.get(packageName) ?: return false
        if (endAtMillis > 0L && grant.endAtMillis > endAtMillis) return false
        grantDao.delete(packageName)
        return true
    }

    /** Grants still running, as package to end-millis, for re-arming expiry alarms. */
    suspend fun liveGrants(nowMillis: Long = System.currentTimeMillis()): List<Pair<String, Long>> =
        grantDao.liveGrants(nowMillis).map { it.packageName to it.endAtMillis }

    /**
     * Housekeeping: purge expired grants/lockouts (called on boot and after an alarm).
     *
     * One transaction, because the intermediate states are real: a process death between
     * clearing a served debt and purging its lockout is harmless, but one between purging
     * the lockout and clearing the debt strands a debt with no timer to serve it.
     */
    suspend fun purgeExpired(nowMillis: Long = System.currentTimeMillis()) {
        database.withTransaction {
            // Read the served debts before their lockout rows are deleted, or a reboot would
            // leave every one of them behind as a debt nothing can clear.
            lockoutDao.expired(nowMillis, LOCKOUT_REASON_DEBT).forEach { debtDao.clear(it.packageName) }
            grantDao.purgeExpired(nowMillis)
            lockoutDao.purgeExpired(nowMillis)
        }
    }

    /**
     * Add [grantedMillis] to the package's tally for the local day containing [nowMillis].
     *
     * Atomic by construction: an UPDATE that adds in place, falling back to starting the day
     * when no row of today's exists. Reading the stored tally and writing the sum back — what
     * this used to do — loses one of two grants that overlap, and the daily cap then under-
     * counts by a whole win.
     */
    private suspend fun recordGrantedUsage(packageName: String, nowMillis: Long, grantedMillis: Long) {
        val today = epochDay(nowMillis)
        val updated = dailyUsageDao.addToDay(packageName, today, grantedMillis)
        if (updated == 0) dailyUsageDao.startDay(packageName, today, grantedMillis)
    }

    private fun TargetEntity?.toOverrides(): TargetOverrides = TargetOverrides(
        winGrantMillis = this?.winGrantMillis,
        lossDebtMillis = this?.lossDebtMillis,
        maxDebtMillis = this?.maxDebtMillis,
        absenceRevokeMillis = this?.absenceRevokeMillis,
        dailyCapMillis = this?.dailyCapMillis,
    )

    /**
     * The rules in force for a target: its overrides, else the build's defaults, then
     * clamped.
     *
     * Clamped on the way *out* as well as on the way in ([updateOverrides]), so a row
     * written before the clamp existed — or by any future caller that bypasses the
     * screen — cannot reach the policy with a value it has no state for.
     */
    private fun TargetEntity?.toRules(): AccessRules = AccessRules(
        winGrantMillis = this?.winGrantMillis ?: RuleDefaults.WIN_GRANT_MILLIS,
        lossDebtMillis = this?.lossDebtMillis ?: AccessPolicy.LOSS_DEBT_MILLIS,
        maxDebtMillis = this?.maxDebtMillis ?: AccessPolicy.MAX_DEBT_MILLIS,
        absenceRevokeMillis = this?.absenceRevokeMillis ?: RuleDefaults.ABSENCE_REVOKE_MILLIS,
        dailyCapMillis = this?.dailyCapMillis,
    ).clamped()

    private fun epochDay(nowMillis: Long): Long =
        Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    private fun GrantEntity.toSnapshot() = GrantSnapshot(packageName, endAtMillis, lastSeenMillis)

    private fun LockoutEntity.toSnapshot() = LockoutSnapshot(packageName, untilMillis, reason)

    companion object {
        /** LockoutEntity.reason written when the user is serving accumulated debt. */
        const val LOCKOUT_REASON_DEBT = "DEBT"

        /** LockoutEntity.reason written when the daily cap is spent. */
        const val LOCKOUT_REASON_DAILY_CAP = "DAILY_CAP"

        /**
         * Smallest debt ceiling worth having. A row storing less than this makes
         * [AccessPolicy.isDebtAtCap] true the moment any debt exists, which locks the app
         * out and closes the table with no hand able to open it again.
         */
        const val MIN_MAX_DEBT_MILLIS = 10_000L

        /** Smallest absence window worth having; a floor under a corrupt or hand-edited row. */
        const val MIN_ABSENCE_REVOKE_MILLIS = 10_000L

        /**
         * How many hands the history keeps. The Stats screen pages at 50 and the tally is
         * two sums, so anything past this is storage and scan cost nobody reads.
         */
        const val HAND_HISTORY_LIMIT = 500

        // One bit per cache table, so "every table has answered" is a single comparison
        // rather than four flags that could be read half-updated.
        private const val TABLE_TARGETS = 0
        private const val TABLE_GRANTS = 1
        private const val TABLE_LOCKOUTS = 2
        private const val TABLE_DEBTS = 3

        internal const val ALL_TABLES_LOADED = (1 shl 4) - 1
    }
}
