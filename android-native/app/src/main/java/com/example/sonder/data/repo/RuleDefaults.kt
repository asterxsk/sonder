package com.example.sonder.data.repo

import com.example.sonder.BuildConfig
import com.example.sonder.domain.AccessPolicy

/**
 * The timers this build runs with, from the `buildConfigField`s the debug variant
 * shortens so enforcement can be verified on-device in seconds rather than minutes.
 *
 * These are the *defaults* a target inherits when it has no override of its own, not a
 * ceiling over the overrides: the release values are the AccessPolicy constants
 * themselves, so a release build behaves exactly as before, while a debug build gets the
 * shortened window. The `minOf` pins the direction down — a build may shorten a timer,
 * never lengthen one past the policy's own constant, however the gradle property is set.
 *
 * A per-app override still wins over these, because it is the more specific choice and
 * the user made it deliberately.
 *
 * Domain stays pure: this is the only place that reads BuildConfig for a policy value,
 * and it lives in the data layer next to the repository that resolves the rules.
 */
object RuleDefaults {
    /** Access granted by a debt-free win. */
    val WIN_GRANT_MILLIS: Long =
        minOf(BuildConfig.ACCESS_WINDOW_MILLIS, AccessPolicy.WIN_GRANT_MILLIS)

    /** Time away from a granted app after which the grant is revoked. */
    val ABSENCE_REVOKE_MILLIS: Long =
        minOf(BuildConfig.ABSENCE_REVOKE_MILLIS, AccessPolicy.ABSENCE_REVOKE_MILLIS)
}
