package com.example.sonder.domain.model

/** Canonical per-target enforcement states (design_v3 §14 badges map onto these). */
enum class EnforcementState {
    /** Not being enforced (target disabled). */
    DISABLED,

    /** Access granted and running. */
    GRANTED,

    /** Locked out — serving debt or cooldown. */
    LOCKED,

    /** Enforced, no active grant, may play blackjack. */
    IDLE,
}
