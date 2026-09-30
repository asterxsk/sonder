package com.example.sonder.data.repo

import androidx.room.withTransaction
import com.example.sonder.data.db.HandDao
import com.example.sonder.data.db.HandEntity
import com.example.sonder.data.db.SonderDatabase
import com.example.sonder.data.db.TargetDao
import com.example.sonder.data.db.TargetEntity
import com.example.sonder.data.db.TimeBankDao
import com.example.sonder.data.db.TimeBankEntity
import com.example.sonder.di.ApplicationScope
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.AccessRules
import com.example.sonder.domain.BlockScope
import com.example.sonder.domain.handNotation
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.Hand
import com.example.sonder.domain.model.HandOutcome
import com.example.sonder.domain.model.TimeBankSnapshot
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.combine

/**
 * Central enforcement store: the single place that mutates targets, access banks and hand
 * history. Domain policy stays pure; this class persists results.
 *
 * Keeps a warm in-memory snapshot of the enforcement state (targets + banks, refreshed on
 * every mutation) so the accessibility hot path can make its decision without Room
 * round-trips. Room remains the source of truth; this cache is only a read-through
 * accelerator and is rebuilt from flows on start.
 */
@Singleton
class EnforcementRepository @Inject constructor(
    private val database: SonderDatabase,
    private val targetDao: TargetDao,
    private val timeBankDao: TimeBankDao,
    private val handDao: HandDao,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val _targets = MutableStateFlow<Map<String, TargetEntity>>(emptyMap())
    private val _banks = MutableStateFlow<Map<String, TimeBankEntity>>(emptyMap())

    /**
     * One bit per cache table, set when that table's flow first answers. Every table has to
     * be in before a decision may be made on the cache: an empty targets map says "not a
     * target" and an empty banks map says "no access", and either answer taken too early is
     * wrong in the direction that lets an app through.
     *
     * Read from the accessibility dispatcher and written from the flows' own coroutines, so
     * it is atomic rather than a plain Boolean.
     */
    private val loadedTables = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile
    private var started = false

    /** True once both cache tables have answered Room at least once. */
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
        timeBankDao.observeAll()
            .onEach { rows ->
                _banks.value = rows.associateBy { it.packageName }
                markLoaded(TABLE_BANKS)
            }
            .launchIn(externalScope)
    }

    fun enabledTarget(pkg: String): TargetEntity? = _targets.value[pkg]?.takeIf { it.enabled }

    /**
     * Every enabled target, from the warm cache.
     *
     * Exists for the one decision that cannot be made from a foreground package: a
     * picture-in-picture window belongs to an app that is *not* in front, so the only way to
     * find one is to walk the targets. Cache-only, because it runs inside the re-check.
     */
    fun enabledPackages(): List<String> =
        _targets.value.values.filter { it.enabled }.map { it.packageName }

    /** The stored bank row, or null when the package has never played a hand. */
    fun cachedBank(pkg: String): TimeBankEntity? = _banks.value[pkg]

    /**
     * Unspent access right now, from the warm cache.
     *
     * Day-aware: a row left over from an earlier local day reads as zero, which is the whole
     * of what the old `daily_usage` table used to enforce. Read on the foreground hot path,
     * so it may not wait on Room.
     */
    fun cachedRemaining(pkg: String, nowMillis: Long = System.currentTimeMillis()): Long {
        val row = _banks.value[pkg] ?: return 0L
        return AccessPolicy.bankAt(row.remainingMillis, row.epochDay, nowMillis)
    }

    /**
     * When the package's removal lock ends, or 0 while it is not locked.
     *
     * Cached rather than read through Room for the same reason as [cachedRemaining]: the
     * settings screen asks about it on every recomposition, and a package with no row is not
     * locked.
     */
    fun cachedRemovalLockedUntil(pkg: String): Long =
        AccessPolicy.removalLockedUntil(_banks.value[pkg]?.emptySinceMillis ?: 0L)

    /**
     * The bank ceiling in force for the package: its stored value, else the default.
     *
     * Clamped here as well as on the way in: a row written before the clamp existed (or by a
     * future caller that bypasses [setMax]) would otherwise let the bank grow to a size the
     * settings screen cannot express and the clamp cannot describe.
     */
    fun cachedMaxMillis(pkg: String): Long =
        (_targets.value[pkg]?.maxMillis ?: AccessPolicy.DEFAULT_MAX_MILLIS)
            .coerceIn(AccessRules.MIN_MAX_MILLIS, AccessRules.MAX_MAX_MILLIS)

    /**
     * How much of the package is gated, from the warm cache.
     *
     * Cached rather than resolved through Room for the same reason as [cachedRemaining]: the
     * live decision path runs on every foreground event and every re-check pass, and a
     * package with no row reads as [BlockScope.WHOLE_APP] — which is what an un-gated
     * package is anyway, and what every row stored before the column existed meant.
     */
    fun cachedBlockScope(pkg: String): BlockScope =
        BlockScope.fromStored(_targets.value[pkg]?.blockScope)

    /** Effective per-app policy: the stored ceiling, else the default, clamped. */
    suspend fun rulesFor(packageName: String): AccessRules =
        AccessRules(maxMillis = targetDao.get(packageName)?.maxMillis ?: AccessPolicy.DEFAULT_MAX_MILLIS)
            .clamped()

    /** Effective per-app policy, re-emitted whenever the target row changes. */
    fun observeRules(packageName: String): Flow<AccessRules> =
        targetDao.observeAll().map { targets ->
            AccessRules(
                maxMillis = targets.find { it.packageName == packageName }?.maxMillis
                    ?: AccessPolicy.DEFAULT_MAX_MILLIS,
            ).clamped()
        }

    /**
     * The rules a chosen ceiling would produce, with no database in the way.
     *
     * Exists for the settings screen's unsaved draft: the screen has to draw the knob as the
     * user has it *right now*. Same default and same clamp as [rulesFor], so a draft and the
     * row it is eventually saved as cannot disagree.
     */
    fun rulesOf(maxMillis: Long?): AccessRules =
        AccessRules(maxMillis = maxMillis ?: AccessPolicy.DEFAULT_MAX_MILLIS).clamped()

    /**
     * Persist the bank ceiling for [packageName], materialising the row if it is missing.
     *
     * The settings screen is reachable from the ALL tab, where every launchable app has a
     * row — including apps never enabled as targets, which have no stored row yet. A missing
     * row therefore materialises here (disabled, so nothing becomes gated) rather than
     * dropping the write. The clamp is applied before it is stored: a ceiling the policy
     * cannot work with is not a preference, it is a state with no way out.
     */
    suspend fun setMax(packageName: String, maxMillis: Long) {
        targetDao.insertIfAbsent(
            TargetEntity(
                packageName = packageName,
                label = packageName,
                enabled = false,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        targetDao.setMax(packageName, rulesOf(maxMillis).maxMillis)
    }

    /** The scope stored for [packageName], or [BlockScope.WHOLE_APP] when it has no row. */
    suspend fun blockScopeFor(packageName: String): BlockScope =
        BlockScope.fromStored(targetDao.get(packageName)?.blockScope)

    /** The stored scope, re-emitted whenever any target row changes. */
    fun observeBlockScope(packageName: String): Flow<BlockScope> =
        targetDao.observeAll().map { targets ->
            BlockScope.fromStored(targets.find { it.packageName == packageName }?.blockScope)
        }

    /**
     * Persist the scope for [packageName], materialising the row if it is missing.
     *
     * Materialises for the same reason [setMax] does: the settings screen is reachable for
     * every launchable app, including ones never enabled as targets, and a scope the user
     * chose must not be dropped for want of a row. The new row is disabled, so choosing a
     * scope is still not the same act as gating the app — ADD does that.
     */
    suspend fun setBlockScope(packageName: String, scope: BlockScope) {
        targetDao.insertIfAbsent(
            TargetEntity(
                packageName = packageName,
                label = packageName,
                enabled = false,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        targetDao.setBlockScope(packageName, scope.stored)
    }

    /** Unspent access for [packageName] at [nowMillis], day-aware. */
    suspend fun remainingMillis(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val row = timeBankDao.get(packageName) ?: return 0L
        return AccessPolicy.bankAt(row.remainingMillis, row.epochDay, nowMillis)
    }

    /** The bank as it stands, re-emitted whenever the row changes or the day rolls over. */
    fun observeRemaining(packageName: String): Flow<Long> =
        timeBankDao.observeAll().map { rows ->
            val row = rows.find { it.packageName == packageName } ?: return@map 0L
            AccessPolicy.bankAt(row.remainingMillis, row.epochDay, System.currentTimeMillis())
        }

    /** Remaining removal lock in millis, or 0 when removal is allowed. */
    fun removalLockRemainingMillis(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long = (cachedRemovalLockedUntil(packageName) - nowMillis).coerceAtLeast(0L)

    /** Observe the enforcement state of one package. */
    fun observeState(packageName: String): Flow<EnforcementState> =
        combine(targetDao.observeAll(), timeBankDao.observeAll()) { targets, banks ->
            val now = System.currentTimeMillis()
            val target = targets.find { it.packageName == packageName }
            val row = banks.find { it.packageName == packageName }
            AccessPolicy.stateFor(
                enabled = target?.enabled ?: false,
                bankMillis = row?.let {
                    AccessPolicy.bankAt(it.remainingMillis, it.epochDay, now)
                } ?: 0L,
            )
        }

    /**
     * Record a finished blackjack hand at [stakeMillis] and return the bank it leaves.
     *
     * One transaction: the bank row and the history row are one state, and a process death
     * between them leaves a ledger that disagrees with the bank the user is actually spending.
     *
     * The stake is recorded with the hand rather than re-derived, because it is a choice the
     * player made and the bank at the time is not recoverable from the row afterwards.
     */
    suspend fun onHandResult(
        packageName: String,
        outcome: HandOutcome,
        stakeMillis: Long,
        playerHand: Hand? = null,
        dealerHand: Hand? = null,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long = database.withTransaction {
        val row = timeBankDao.get(packageName)
        val today = epochDay(nowMillis)
        val bankBefore = row?.let { AccessPolicy.bankAt(it.remainingMillis, it.epochDay, nowMillis) } ?: 0L
        val stake = stakeMillis.coerceAtLeast(0L)
        val bankAfter = AccessPolicy.onHandResult(
            outcome = outcome,
            bankMillis = bankBefore,
            stakeMillis = stake,
            maxMillis = rulesFor(packageName).maxMillis,
        )

        // The removal lock is stamped when the bank is left empty and cleared the moment it
        // is not. A bank that was already empty and stays empty keeps the original stamp, so
        // a run of losing hands cannot keep pushing the lock further out.
        val emptySince = when {
            bankAfter > 0L -> 0L
            // A bank that was already empty and stays empty keeps the stamp it had, so a run
            // of losing hands cannot keep pushing the removal lock further out.
            else -> row?.emptySinceMillis?.takeIf { it > 0L } ?: nowMillis
        }

        timeBankDao.upsert(
            TimeBankEntity(
                packageName = packageName,
                remainingMillis = bankAfter,
                epochDay = today,
                // The stamp is written to *now* even though nothing has been billed: the
                // next drain measures from it, and a stamp left at the moment before the
                // hand would bill the user for the time the table was open.
                lastSeenMillis = nowMillis,
                emptySinceMillis = emptySince,
            ),
        )
        handDao.insert(
            HandEntity(
                packageName = packageName,
                outcome = outcome.name,
                stakeMillis = stake,
                bankAfterMillis = bankAfter,
                playedAtMillis = nowMillis,
                // Snapshot, from the cache: the history has to say which app this was and
                // what the hand looked like, and neither is recoverable later — the target
                // can be renamed and the gate drops the cards the moment the table resets.
                label = _targets.value[packageName]?.label?.takeIf { it.isNotBlank() } ?: packageName,
                playerCards = handNotation(playerHand),
                dealerCards = handNotation(dealerHand),
            ),
        )
        handDao.trimTo(HAND_HISTORY_LIMIT)
        bankAfter
    }

    /**
     * Bill the foreground time since the package was last seen and return the bank left.
     *
     * The elapsed is measured from the row's own stamp, so only time the app was in front is
     * ever charged — the coordinator refreshes that stamp while the app is on screen and
     * never while it is not. `AccessPolicy.bill` clamps the gap, so a process death, a
     * reboot, or a stalled service cannot charge for time nobody was present for.
     *
     * Reaching zero is the one event the old expiry alarm used to announce, so it is
     * announced from here instead rather than the notification silently going dead.
     *
     * @return the bank after billing; 0 when it was already empty.
     */
    suspend fun billUsage(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long = database.withTransaction {
        val row = timeBankDao.get(packageName) ?: return@withTransaction 0L
        val today = epochDay(nowMillis)
        // A row from an earlier day is spent by definition, and carries no elapsed worth
        // billing: the day rolled over while the app was not in front.
        val bankBefore = AccessPolicy.bankAt(row.remainingMillis, row.epochDay, nowMillis)
        val elapsed = if (row.epochDay == today) nowMillis - row.lastSeenMillis else 0L
        val bankAfter = AccessPolicy.bill(bankBefore, elapsed)

        val emptied = bankAfter == 0L && bankBefore > 0L
        val emptySince = when {
            bankAfter > 0L -> 0L
            emptied -> nowMillis
            else -> row.emptySinceMillis
        }

        timeBankDao.upsert(
            row.copy(
                remainingMillis = bankAfter,
                epochDay = today,
                lastSeenMillis = nowMillis,
                emptySinceMillis = emptySince,
            ),
        )
        if (emptied) notifyBankDrained(packageName)
        bankAfter
    }

    /**
     * Empty the bank for [packageName] and report whether anything was taken.
     *
     * Exists for housekeeping and for tests: nothing in the app spends a bank except the
     * drain and a lost hand.
     */
    suspend fun clearBank(packageName: String, nowMillis: Long = System.currentTimeMillis()) {
        val row = timeBankDao.get(packageName) ?: return
        timeBankDao.upsert(
            row.copy(
                remainingMillis = 0L,
                epochDay = epochDay(nowMillis),
                emptySinceMillis = row.emptySinceMillis.takeIf { it > 0L } ?: nowMillis,
            ),
        )
    }

    /**
     * Remove a target: the row, its bank, and nothing else.
     *
     * The hand history is deliberately kept, as it always was — it is a ledger of what was
     * played, not a property of the target. Refused while the removal lock is running, so
     * this is the one place that has to enforce it and the screen does not have to be
     * trusted to.
     *
     * @return true when the target was removed.
     */
    suspend fun removeTarget(
        packageName: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = database.withTransaction {
        if (AccessPolicy.isRemovalLocked(cachedBank(packageName)?.emptySinceMillis ?: 0L, nowMillis)) {
            return@withTransaction false
        }
        targetDao.delete(packageName)
        timeBankDao.delete(packageName)
        true
    }

    /** Is this package an enabled target? */
    suspend fun isTargetEnabled(packageName: String): Boolean =
        targetDao.get(packageName)?.enabled == true

    /** The bank as a domain snapshot, or null when the package has no row. */
    suspend fun bankSnapshot(packageName: String): TimeBankSnapshot? =
        timeBankDao.get(packageName)?.toSnapshot()

    private fun TimeBankEntity.toSnapshot() =
        TimeBankSnapshot(packageName, remainingMillis, epochDay, lastSeenMillis, emptySinceMillis)

    private fun epochDay(nowMillis: Long): Long =
        Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /**
     * Announce that the bank has run out.
     *
     * This layer holds no Context, so it cannot post the notification itself; the coordinator
     * registers [onBankDrained] and does. The seam is here rather than in the coordinator
     * because "the bank just hit zero" is decided by the drain, one place, and an announcer
     * that had to re-derive it would be a second answer to the same question.
     */
    private fun notifyBankDrained(packageName: String) {
        drainListener?.invoke(packageName)
    }

    @Volatile
    private var drainListener: ((String) -> Unit)? = null

    /** Register the one callback fired when a bank drains to zero. */
    fun onBankDrained(listener: (String) -> Unit) {
        drainListener = listener
    }

    companion object {
        /**
         * How many hands the history keeps. The Stats screen pages at 50 and the tally is
         * two sums, so anything past this is storage and scan cost nobody reads.
         */
        const val HAND_HISTORY_LIMIT = 500

        // One bit per cache table, so "every table has answered" is a single comparison
        // rather than two flags that could be read half-updated.
        private const val TABLE_TARGETS = 0
        private const val TABLE_BANKS = 1

        internal const val ALL_TABLES_LOADED = (1 shl 2) - 1
    }
}
