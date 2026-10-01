package com.example.sonder.ui.permissions

import com.example.sonder.platform.permissions.SonderPermission

/**
 * What each permission is *for*, in the words the user is shown.
 *
 * Shared by the two surfaces that ask for a grant — the onboarding wizard, which walks them
 * one at a time with room to explain, and the on-open reminder, which has to say the same
 * things in a card the user did not ask for and must be able to dismiss. They are one file
 * because they are one answer: a second copy of this text is how the wizard ends up
 * describing usage access as a fallback while the reminder calls it something else, and the
 * user is asked for the same grant twice in two different voices.
 *
 * @param title the permission's own name, in the pixel face's uppercase, as the platform
 *   names it in Settings — the user has to find that exact row in another app.
 * @param body the wizard's paragraph: what breaks without it, and why the app needs it.
 * @param reassure the privacy answer, shown under the body. Kept separate because the
 *   reminder has no room for it and must not paraphrase it.
 * @param why the one-line version, for the reminder. It is the *need*, not the name: the
 *   user has already decided to install this app, and what they are missing in a card is
 *   why the grant is worth leaving the app for.
 */
data class StepCopy(
    val title: String,
    val body: String,
    val reassure: String,
    val why: String,
)

fun copyFor(permission: SonderPermission): StepCopy = when (permission) {
    SonderPermission.ACCESSIBILITY -> StepCopy(
        title = "ACCESSIBILITY",
        body = "Sonder needs to know which app you just opened — that's the whole trigger. It watches window changes, and reads view names to tell Reels, Shorts and Stories from the rest of an app.",
        reassure = "View names are developer labels, not what's on screen. No screen content, no keystrokes. Ever.",
        why = "Knows which app you just opened. Nothing else.",
    )

    SonderPermission.OVERLAY -> StepCopy(
        title = "DISPLAY OVER OTHER APPS",
        body = "The block screen must appear the instant a limited app opens — before the table even loads.",
        reassure = "One full-screen pixel frame, nothing else.",
        why = "Lets the blackjack gate sit over the app.",
    )

    SonderPermission.USAGE_ACCESS -> StepCopy(
        title = "USAGE ACCESS",
        body = "A second way to see which app is in front, for when accessibility is slow to wake up. The gate leans on it as a fallback.",
        reassure = "Data stays on this device. Nothing is uploaded.",
        why = "A second opinion on which app is in front.",
    )

    SonderPermission.NOTIFICATIONS -> StepCopy(
        title = "NOTIFICATIONS — OPTIONAL",
        body = "Tells you when an app's bank runs dry — otherwise you'd only find out at the next gate.",
        reassure = "Only enforcement alerts. No marketing, ever. Skip it and blocking still works.",
        why = "One heads-up when a bank runs dry. Optional.",
    )
}
