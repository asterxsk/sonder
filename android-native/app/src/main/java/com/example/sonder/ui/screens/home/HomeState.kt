package com.example.sonder.ui.screens.home

import com.example.sonder.data.db.TargetEntity
import com.example.sonder.data.repo.EnforcementRepository
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Which lockout a row is serving. A debt lockout is short and ticks down; a daily-cap
 * lockout runs to the next local midnight, so Home presents them differently. Null
 * `LockoutSnapshot.reason` (pre-reason rows) can only ever have been debt.
 */
enum class HomeLockout { DEBT, DAILY_CAP }

/**
 * Home's badge vocabulary. PLAYING is deliberately absent: while a gated app is open the
 * block overlay covers Home, and Home is only on screen when Sonder itself is foreground,
 * so "playing" was never observable here — the blackjack gate keeps that badge.
 */
enum class HomeBadge { GRANTED, LOCKED }

/** One enabled target as Home renders it: canonical state plus live remaining text. */
data class HomeRow(
    val packageName: String,
    val label: String,
    val state: EnforcementState,
    /** `MM:SS` while a grant or a debt lockout is running; empty when nothing counts down. */
    val remainingText: String,
    /** The lockout kind when [state] is LOCKED; null otherwise. */
    val lockout: HomeLockout? = null,
    /** Local wall-clock `HH:mm` a DAILY_CAP row resets at; empty for every other row. */
    val resetText: String = "",
)

/**
 * GRANTED only for a live grant. IDLE is LOCKED because the app is gated and untouched:
 * no clock is running behind it, and no Home state could honestly read as "playing".
 */
internal fun HomeRow.badge(): HomeBadge =
    if (state == EnforcementState.GRANTED) HomeBadge.GRANTED else HomeBadge.LOCKED

/**
 * The badge's own word when null, so an idle row reads a clean `LOCKED` rather than one
 * with a trailing space where a countdown would be. A cap lockout names the wall clock it
 * resets against instead of a countdown that would run four digits long.
 */
internal fun HomeRow.badgeText(): String? = when {
    lockout == HomeLockout.DAILY_CAP -> "CAPPED"
    else -> remainingText.ifEmpty { null }
}

/**
 * Readable state word for the row's accessibility description. Idle reads LOCKED so the
 * spoken state matches the visible badge.
 */
internal fun HomeRow.stateWord(): String = when {
    lockout == HomeLockout.DAILY_CAP -> "DAILY CAP REACHED"
    state == EnforcementState.LOCKED -> "LOCKED"
    state == EnforcementState.GRANTED -> "ACCESS GRANTED"
    state == EnforcementState.IDLE -> "LOCKED"
    else -> "OFF"
}

/** The live-status panel's deterministic presentations (Loading is the screen's null state). */
sealed interface HomeSummary {
    /** Nothing is limited: the LIMIT APPS prompt. */
    data object NoTargets : HomeSummary

    /** Apps are limited but none is active; [enabledCount] is how many are limited. */
    data class Idle(val enabledCount: Int) : HomeSummary

    /** The highest-urgency granted-or-locked row. */
    data class Active(val row: HomeRow) : HomeSummary
}

/**
 * Mapper result: the panel summary plus the ordered enabled rows. Public because
 * [HomeViewModel.state] exposes it; the mapper below stays internal.
 */
data class HomeState(
    val summary: HomeSummary,
    val rows: List<HomeRow>,
)

/**
 * Pure Home mapper. Time arrives as [nowMillis] — never read from the clock in here —
 * so countdown text is deterministic in tests. Grants and lockouts are indexed with
 * `associateBy` before the target walk, so one refresh costs
 * `O(targets + grants + lockouts)` instead of scanning both lists per target.
 */
internal fun mapHomeState(
    targets: List<TargetEntity>,
    grants: List<GrantSnapshot>,
    lockouts: List<LockoutSnapshot>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): HomeState {
    val grantByPackage = grants.associateBy { it.packageName }
    val lockoutByPackage = lockouts.associateBy { it.packageName }

    val rows = targets
        .filter { it.enabled } // disabled records stay in Room and appear only on Targets
        .map { target ->
            val grant = grantByPackage[target.packageName]
            val lockout = lockoutByPackage[target.packageName]
            val state = AccessPolicy.stateFor(
                packageName = target.packageName,
                enabled = true,
                grant = grant,
                lockout = lockout,
                nowMillis = nowMillis,
            )
            val kind = if (state == EnforcementState.LOCKED) {
                lockout?.let { lockoutKind(it.reason) }
            } else {
                null
            }
            // A cap lockout runs to the next local midnight: it has a reset wall clock,
            // not an interval, so it must never turn into a ticking MM:SS countdown.
            val endsAtMillis = when {
                state == EnforcementState.GRANTED -> grant?.endAtMillis
                kind == HomeLockout.DEBT -> lockout?.untilMillis
                else -> null
            }
            HomeRow(
                packageName = target.packageName,
                label = target.label,
                state = state,
                remainingText = endsAtMillis
                    ?.minus(nowMillis)
                    ?.takeIf { it > 0L }
                    ?.let(::formatRemaining)
                    ?: "",
                lockout = kind,
                resetText = if (kind == HomeLockout.DAILY_CAP) {
                    lockout?.untilMillis?.let { formatResetClock(it, zoneId) } ?: ""
                } else {
                    ""
                },
            )
        }
        .sortedWith(compareBy<HomeRow>({ urgency(it) }, { it.label }))

    val active = rows.firstOrNull {
        it.state == EnforcementState.LOCKED || it.state == EnforcementState.GRANTED
    }
    val summary = when {
        rows.isEmpty() -> HomeSummary.NoTargets
        active != null -> HomeSummary.Active(active)
        else -> HomeSummary.Idle(enabledCount = rows.size)
    }
    return HomeState(summary = summary, rows = rows)
}

/**
 * Panel and list urgency. A debt lockout is short and actionable, so it outranks a
 * live grant. A cap lockout lasts the rest of the day and is not actionable, so it
 * ranks below a grant that may expire in minutes. Idle comes last.
 */
private fun urgency(row: HomeRow): Int = when {
    row.lockout == HomeLockout.DEBT -> 0
    row.state == EnforcementState.GRANTED -> 1
    row.lockout == HomeLockout.DAILY_CAP -> 2
    row.state == EnforcementState.IDLE -> 3
    else -> 4 // DISABLED never reaches here (filtered above); keep the mapping total
}

/** A null reason (a pre-reason row) can only ever have been debt. */
private fun lockoutKind(reason: String?): HomeLockout =
    if (reason == EnforcementRepository.LOCKOUT_REASON_DAILY_CAP) HomeLockout.DAILY_CAP
    else HomeLockout.DEBT

/** Absolute epoch millis to the `MM:SS` the pixel timer shows. */
internal fun formatRemaining(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}

/** Absolute epoch millis to the local wall-clock `HH:mm` a cap lockout resets at. */
internal fun formatResetClock(millis: Long, zoneId: ZoneId): String =
    ResetClock.format(Instant.ofEpochMilli(millis).atZone(zoneId))

private val ResetClock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
