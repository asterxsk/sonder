package com.example.sonder.domain.model

/**
 * Lightweight snapshots the repository hands to the policy — kept in the domain
 * package so AccessPolicy stays free of persistence types.
 */
data class TimeBankSnapshot(
    val packageName: String,
    /** Unspent access. Zero means out of time, which is what the gate is. */
    val remainingMillis: Long,
    /** Local day the bank belongs to; a read on a later day reads as zero. */
    val epochDay: Long,
    /** Last moment the app was in front and billed; foreground elapsed is measured from here. */
    val lastSeenMillis: Long,
    /**
     * When the bank last reached zero, or 0 while it has not. Removal of the target is
     * refused for [com.example.sonder.domain.AccessPolicy.REMOVAL_LOCK_MILLIS] after it.
     */
    val emptySinceMillis: Long = 0L,
)
