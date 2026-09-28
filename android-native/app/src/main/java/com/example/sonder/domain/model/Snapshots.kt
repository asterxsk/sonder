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
    /**
     * Mirror of `LockoutEntity.reason` as a plain string so the domain stays free of
     * persistence types: "DEBT" | "DAILY_CAP", with null (rows written before reasons
     * existed) read as DEBT. The constants live on EnforcementRepository.
     */
    val reason: String? = null,
)
