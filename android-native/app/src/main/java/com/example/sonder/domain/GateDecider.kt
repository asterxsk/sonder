package com.example.sonder.domain

/** What the coordinator must do for one foreground window event. */
enum class GateDecision {
    /** The package is not an enabled target — release any blocker. */
    PASS,

    /** The bank holds time — let the app run, and bill the foreground time against it. */
    GRANTED,

    /** Enabled target, empty bank — raise the blocker (blackjack table). */
    GATE,
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
     */
    fun decide(
        targetEnabled: Boolean,
        bankMillis: Long,
        blockScope: BlockScope = BlockScope.WHOLE_APP,
        scopedSurfacePresent: Boolean = true,
    ): GateDecision {
        if (!targetEnabled) return GateDecision.PASS

        // A scoped target is a target only while its own surface is on screen. This is
        // checked *before* the bank, not after, because the two answer different questions:
        // the bank says "the user earned time in this app", and the scope says "this window
        // is part of the app the user asked to gate at all". Winning a hand to watch Reels
        // must not turn the Instagram feed into a gated surface, or the bank would unlock a
        // screen the target was never about.
        if (blockScope == BlockScope.SHORTS_ONLY && !scopedSurfacePresent) return GateDecision.PASS

        return if (bankMillis > 0L) GateDecision.GRANTED else GateDecision.GATE
    }
}
