package com.example.sonder.domain.model

/** Canonical per-target enforcement states (design_v3 §14 badges map onto these). */
enum class EnforcementState {
    /** Not being enforced (target disabled). */
    DISABLED,

    /** The bank has time in it: the app is open. */
    GRANTED,

    /** Enforced, bank empty, may play blackjack. */
    IDLE,
}
