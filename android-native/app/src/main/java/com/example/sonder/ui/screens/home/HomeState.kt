package com.example.sonder.ui.screens.home

import com.example.sonder.data.db.TargetEntity
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.TimeBankSnapshot
import java.time.ZoneId
import java.util.Locale

/**
 * Home's badge vocabulary. PLAYING is deliberately absent: while a gated app is open the
 * block overlay covers Home, and Home is only on screen when Sonder itself is foreground,
 * so "playing" was never observable here — the blackjack gate keeps that badge.
 */
enum class HomeBadge { GRANTED, LOCKED }

/** One enabled target as Home renders it: canonical state plus the bank behind it. */
data class HomeRow(
    val packageName: String,
    val label: String,
    val state: EnforcementState,
    /** Time this app has banked right now, after the day check; 0 when it has none. */
    val bankMillis: Long,
    /** `MM:SS` while there is time in the bank; empty when there is none to count. */
    val remainingText: String,
)

/**
 * GRANTED only while there is time in the bank. IDLE is the gated, untouched app — which is
 * what an empty bank is — and no Home state could honestly read as anything else.
 */
internal fun HomeRow.badge(): HomeBadge =
    if (state == EnforcementState.GRANTED) HomeBadge.GRANTED else HomeBadge.LOCKED

/** The badge's own word when null, so an idle row reads a clean `LOCKED` with no countdown. */
internal fun HomeRow.badgeText(): String? = remainingText.ifEmpty { null }

/**
 * Readable state word for the row's accessibility description. Idle reads LOCKED so the
 * spoken state matches the visible badge.
 */
internal fun HomeRow.stateWord(): String = when (state) {
    EnforcementState.GRANTED -> "ACCESS GRANTED"
    EnforcementState.IDLE -> "LOCKED"
    else -> "OFF"
}

/** The live-status panel's deterministic presentations (Loading is the screen's null state). */
sealed interface HomeSummary {
    /** Nothing is limited: the LIMIT APPS prompt. */
    data object NoTargets : HomeSummary

    /** Apps are limited but none has time banked; [enabledCount] is how many are limited. */
    data class Idle(val enabledCount: Int) : HomeSummary

    /** The row with the least time left, which is the one about to matter. */
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
 * Pure Home mapper. Time arrives as [nowMillis] — never read from the clock in here — so
 * countdown text is deterministic in tests.
 *
 * A bank from an earlier day reads as nothing, which is what makes the allowance daily
 * without a table of its own: yesterday's leftovers are not today's access, so every app
 * starts the day gated.
 */
internal fun mapHomeState(
    targets: List<TargetEntity>,
    banks: List<TimeBankSnapshot>,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): HomeState {
    val bankByPackage = banks.associateBy { it.packageName }

    val rows = targets
        .filter { it.enabled } // disabled records stay in Room and appear only on Targets
        .map { target ->
            val bank = bankByPackage[target.packageName]
            val remaining = bank?.let {
                AccessPolicy.bankAt(
                    remainingMillis = it.remainingMillis,
                    epochDay = it.epochDay,
                    nowMillis = nowMillis,
                    zoneId = zoneId,
                )
            } ?: 0L
            val state = AccessPolicy.stateFor(enabled = true, bankMillis = remaining)
            HomeRow(
                packageName = target.packageName,
                label = target.label,
                state = state,
                bankMillis = remaining,
                remainingText = if (remaining > 0L) formatRemaining(remaining) else "",
            )
        }
        .sortedWith(compareBy<HomeRow>({ urgency(it) }, { it.bankMillis }, { it.label }, { it.packageName }))

    // The panel names the bank that is about to run out rather than the first row in the
    // list, because that is the one the user can still do something about.
    val active = rows
        .filter { it.state == EnforcementState.GRANTED }
        .minByOrNull { it.bankMillis }
    val summary = when {
        rows.isEmpty() -> HomeSummary.NoTargets
        active != null -> HomeSummary.Active(active)
        else -> HomeSummary.Idle(enabledCount = rows.size)
    }
    return HomeState(summary = summary, rows = rows)
}

/**
 * List order. An app with time banked is what the screen is about, and the smaller the
 * bank the sooner it matters; the rest are all equally idle and fall back to name order.
 * The package name is the last tiebreak because two apps can share a label — the sort is
 * total, so the list never reshuffles when Room returns the same rows in another order.
 */
private fun urgency(row: HomeRow): Int =
    if (row.state == EnforcementState.GRANTED) 0 else 1

/** Absolute epoch millis to the `MM:SS` the pixel timer shows. */
internal fun formatRemaining(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}
