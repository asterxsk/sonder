package com.example.sonder.ui.screens.targets

/**
 * The two delayed row controls. [holdMillis] is the whole feature: each action must
 * cost enough time that it cannot be reached for in the moment the app is wanted, so
 * the wait is a property of the action, not a screen-level constant. EDIT is short
 * enough to stay usable while reconfiguring; REMOVE is long enough to be a decision.
 */
enum class TargetActionKind(val holdMillis: Long) {
    EDIT(15_000L),
    REMOVE(30_000L),
}

/**
 * One countdown for the whole screen. [remainingMillis] only ever decreases by a
 * tick, so a countdown resumed after a recomposition cannot fast-forward a wait.
 */
data class PendingTargetAction(
    val packageName: String,
    val kind: TargetActionKind,
    val remainingMillis: Long,
)

/** Whole seconds still owed, floored at zero, for the strip's readout. */
fun PendingTargetAction.remainingSeconds(): Long =
    (remainingMillis / 1000L).coerceAtLeast(0L)

/** `M:SS` — minutes carry no leading zero, matching the app's other clocks. */
fun formatHoldClock(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    return "${safe / 60L}:${(safe % 60L).toString().padStart(2, '0')}"
}

/** `REMOVE 0:24` / `EDIT 0:15` — the strip's entire text. */
fun PendingTargetAction.label(): String = "${kind.name} ${formatHoldClock(remainingSeconds())}"

/**
 * One tick of the hold. Null the instant the wait is spent — that is the screen's
 * signal to fire and clear, so a `0:00` state never renders and the strip cannot sit
 * on a finished countdown.
 */
fun PendingTargetAction.tick(tickMillis: Long = 1_000L): PendingTargetAction? {
    val next = remainingMillis - tickMillis
    return if (next <= 0L) null else copy(remainingMillis = next)
}

/** A fresh countdown for a tap on [kind]; the hold length comes from the kind itself. */
fun startPendingAction(packageName: String, kind: TargetActionKind): PendingTargetAction =
    PendingTargetAction(packageName = packageName, kind = kind, remainingMillis = kind.holdMillis)
