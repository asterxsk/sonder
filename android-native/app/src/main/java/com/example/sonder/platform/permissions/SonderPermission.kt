package com.example.sonder.platform.permissions

/**
 * The four grants the app can ask for.
 *
 * [optional] separates the ones enforcement actually runs on from the one that is only a
 * courtesy. Without accessibility, the overlay, or usage access there is no gate at all —
 * those stay hard requirements and the wizard will not move past them. Notifications carry
 * one heads-up ("time's up") and nothing else: if they are off the block still happens, and
 * the only loss is that the user has to notice the timer themselves. So it is the one step
 * that can be skipped, and every surface that nags about a missing permission reads this
 * flag rather than hard-coding which name to skip.
 *
 * The enum and the two rules below are deliberately free of Android imports: the wizard, the
 * on-open reminder and the settings row all ask these questions, and they are answerable —
 * and testable — without a device.
 */
enum class SonderPermission(val label: String, val optional: Boolean = false) {
    ACCESSIBILITY("Accessibility"),
    OVERLAY("Display over other apps"),
    USAGE_ACCESS("Usage access"),
    NOTIFICATIONS("Notifications", optional = true),
}

/**
 * [missing] less the optional permissions the user has turned down — what is still worth
 * asking about.
 *
 * Lives beside the enum because two very different surfaces ask the same question: the
 * wizard, deciding whether it has anything left to do, and the on-open reminder, deciding
 * whether it has anything left to say. Both went to the audit for the raw set and would each
 * have had to re-derive this rule; with the rule in one place, "optional means we stop
 * asking" cannot hold on one screen and not on the other.
 */
fun outstandingPermissions(
    missing: Set<SonderPermission>,
    skippedNames: Set<String>,
): Set<SonderPermission> =
    missing.filterTo(mutableSetOf()) { !(it.optional && it.name in skippedNames) }

/**
 * The enum entries named in [names].
 *
 * The skip list is stored as names, and a name this build no longer knows is dropped rather
 * than kept as an opaque string: a permission removed from the enum must not sit in the
 * settings list forever because an old install wrote it down.
 */
fun skippedPermissionsNamed(names: Set<String>): Set<SonderPermission> =
    SonderPermission.entries.filterTo(mutableSetOf()) { it.name in names }
