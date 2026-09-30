package com.example.sonder.domain

/**
 * The per-app access policy, which is now one number: the ceiling on the time bank.
 *
 * The debt model this used to describe — grant per win, penalty per loss, a ceiling on the
 * debt and an absence window — is gone. Access is a bank a hand credits and the app's own
 * foreground time drains, so the only thing left to configure per app is how much the bank
 * may hold at once.
 */
data class AccessRules(
    /** Most unspent access the bank may hold. Default is one hour. */
    val maxMillis: Long = AccessPolicy.DEFAULT_MAX_MILLIS,
) {
    /**
     * The same rules with the value pulled inside the range the bank can work with.
     *
     * An override is user input reaching the policy unexamined, and the model has states a
     * nonsensical one falls into rather than rejects: a ceiling of zero makes every win credit
     * nothing, so the app could never be opened again and the table would be an unbounded
     * session against a bank that cannot grow. Nothing downstream re-checks this, so the clamp
     * belongs here — one place, pure, and unit-testable — and both the read path and the write
     * path go through it.
     */
    fun clamped(): AccessRules = copy(
        maxMillis = maxMillis.coerceIn(MIN_MAX_MILLIS, MAX_MAX_MILLIS),
    )

    companion object {
        /** Below a minute the ceiling stops being a ceiling and starts being a wall. */
        const val MIN_MAX_MILLIS = 60_000L

        /** A day: past this every preset is meaningless and the day's refill is the cap. */
        const val MAX_MAX_MILLIS = 24 * 60 * 60_000L
    }
}
