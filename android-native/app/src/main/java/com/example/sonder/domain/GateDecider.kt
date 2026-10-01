package com.example.sonder.domain

/** What the coordinator must do for one foreground window event. */
enum class GateDecision {
    /** The package is not an enabled target — release any blocker. */
    PASS,

    /**
     * The user has won their way in for this visit — let the app run, and bill the
     * foreground time against the bank.
     */
    GRANTED,

    /** The bank holds time but the user has not won their way in — raise the table. */
    GATE,

    /** The bank is spent — raise the wall. There is no table, and nothing to play for. */
    LOCKED,
}

/**
 * Pure per-event decision core (no Android, no persistence): given the cached
 * enforcement state of one package at the moment it reaches the foreground,
 * decide what the platform layer must do. Unit-tested so the coordinator and
 * the overlay host stay thin.
 */
object GateDecider {

    /**
     * @param targetEnabled package is an enabled target
     * @param bankMillis    unspent access in the package's bank, already read for today
     * @param blockScope how much of the target is gated — the whole app, or only its
     *   short-form video surface.
     * @param scopedSurfacePresent whether that surface is the one on screen. Answered by the
     *   caller because recognising it means reading a window's node tree, which this core
     *   cannot do; ignored entirely for [BlockScope.WHOLE_APP].
     * @param entered whether the user has already won their way into this app for this
     *   visit. Answered by the caller because it is session state — a won hand, held until
     *   the user leaves the app — and this core holds nothing. Spending the bank is not
     *   what lets anyone in; a hand is.
     */
    fun decide(
        targetEnabled: Boolean,
        bankMillis: Long,
        blockScope: BlockScope = BlockScope.WHOLE_APP,
        scopedSurfacePresent: Boolean = true,
        entered: Boolean = false,
    ): GateDecision {
        if (!targetEnabled) return GateDecision.PASS

        // A scoped target is a target only while its own surface is on screen. This is
        // checked *before* the bank, not after, because the two answer different questions:
        // the bank says "the user earned time in this app", and the scope says "this window
        // is part of the app the user asked to gate at all". Winning a hand to watch Reels
        // must not turn the Instagram feed into a gated surface, or the bank would unlock a
        // screen the target was never about.
        if (blockScope == BlockScope.SHORTS_ONLY && !scopedSurfacePresent) return GateDecision.PASS

        // The bank is read before the entry: a grant is only ever held over time that still
        // exists, so a bank that empties mid-use drops to the wall on the next pass rather
        // than waiting for the user to leave and come back.
        return when {
            bankMillis <= 0L -> GateDecision.LOCKED
            entered -> GateDecision.GRANTED
            else -> GateDecision.GATE
        }
    }
}
