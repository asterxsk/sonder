package com.example.sonder.ui.screens.home

import com.example.sonder.data.db.TargetEntity
import com.example.sonder.domain.AccessPolicy
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.domain.model.GrantSnapshot
import com.example.sonder.domain.model.LockoutSnapshot
import java.util.Locale

/** One enabled target as Home renders it: canonical state plus live remaining text. */
data class HomeRow(
    val packageName: String,
    val label: String,
    val state: EnforcementState,
    /** `MM:SS` while a grant or lockout is running; empty when nothing counts down. */
    val remainingText: String,
)

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
            val endsAtMillis = when (state) {
                EnforcementState.GRANTED -> grant?.endAtMillis
                EnforcementState.LOCKED -> lockout?.untilMillis
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
            )
        }
        .sortedWith(compareBy<HomeRow>({ urgency(it.state) }, { it.label }))

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

/** Panel and list urgency: serving a lockout outranks a live grant; idle comes last. */
private fun urgency(state: EnforcementState): Int = when (state) {
    EnforcementState.LOCKED -> 0
    EnforcementState.GRANTED -> 1
    EnforcementState.IDLE -> 2
    EnforcementState.DISABLED -> 3
}

/** Absolute epoch millis to the `MM:SS` the pixel timer shows. */
internal fun formatRemaining(millis: Long): String {
    val totalSeconds = millis / 1000
    return String.format(Locale.ROOT, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)
}
