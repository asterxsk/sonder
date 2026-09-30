package com.example.sonder.domain

/**
 * Per-app policy overrides. Every field defaults to the global AccessPolicy
 * constant, so a target with no overrides behaves exactly as before.
 */
data class AccessRules(
    val winGrantMillis: Long = AccessPolicy.WIN_GRANT_MILLIS,
    val lossDebtMillis: Long = AccessPolicy.LOSS_DEBT_MILLIS,
    val maxDebtMillis: Long = AccessPolicy.MAX_DEBT_MILLIS,
    val absenceRevokeMillis: Long = AccessPolicy.ABSENCE_REVOKE_MILLIS,
    /** Total access this app may be granted per local day; null means unlimited. */
    val dailyCapMillis: Long? = null,
) {
    /**
     * The same rules with every override pulled inside the range the policy can work with.
     *
     * An override is user input that reaches the debt model unexamined, and the model has
     * states a nonsensical one falls into rather than rejects: `maxDebtMillis = 0` makes
     * [AccessPolicy.isDebtAtCap] true at all times, which locks the app out for good and
     * closes the table ([AccessPolicy.isDebtAtCap] is what stops hands being dealt), and a
     * `dailyCapMillis = 0` spends the cap before it is ever used, so no hand can grant
     * access again. A negative [lossDebtMillis] is worse than either: a loss would *refund*
     * debt. Nothing downstream re-checks these, so the clamp belongs here — one place, pure,
     * and unit-testable — and both the read path and the write path go through it.
     */
    fun clamped(): AccessRules = copy(
        winGrantMillis = winGrantMillis.coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS),
        lossDebtMillis = lossDebtMillis.coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS),
        maxDebtMillis = maxDebtMillis.coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS),
        absenceRevokeMillis = absenceRevokeMillis.coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS),
        dailyCapMillis = dailyCapMillis?.coerceIn(MIN_SPAN_MILLIS, MAX_SPAN_MILLIS),
    )

    private companion object {
        /** Short enough to be a real choice, long enough not to expire while it is applied. */
        const val MIN_SPAN_MILLIS = 10_000L

        /** A day: past this every preset is meaningless and the cap stops being a cap. */
        const val MAX_SPAN_MILLIS = 24 * 60 * 60_000L
    }
}
