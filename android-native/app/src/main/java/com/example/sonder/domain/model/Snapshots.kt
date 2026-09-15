package com.example.sonder.domain.model

/**
 * Lightweight snapshots the repository hands to the policy — kept in the domain
 * package so AccessPolicy stays free of persistence types.
 */
data class GrantSnapshot(
    val packageName: String,
    val endAtMillis: Long,
    val lastSeenMillis: Long,
)

data class LockoutSnapshot(
    val packageName: String,
    val untilMillis: Long,
    val debtMillis: Long,
)
