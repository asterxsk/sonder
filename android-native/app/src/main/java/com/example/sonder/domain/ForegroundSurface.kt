package com.example.sonder.domain

/**
 * What kind of surface just became the foreground window.
 *
 * A system-level blocker must react per surface: only real applications can be
 * gated; transient system surfaces (notification shade, IME, keyguard) must
 * never disturb the blocker, and home/recents/the detox app itself must
 * release it. Pure and unit-tested so the accessibility service stays thin.
 */
enum class ForegroundSurface {
    /** The detox app itself — always allowed; the blocker releases. */
    OWN,

    /** Transient system surface floating above the current app. */
    TRANSIENT,

    /** Home screen / launcher / recents — release the blocker, never gate. */
    HOME,

    /** A real application — the only surface that may be gated. */
    APP,
    ;

    companion object {
        /** System packages whose windows float above an app without replacing it. */
        val TRANSIENT_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.systemui.navigationbar",
            "com.android.keyguard",
            "com.android.internal.systemui.navbar.gestural",
            "android",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
        )

        /** IME hints — kept precise so no real app is misread as a keyboard. */
        private val IME_HINTS = listOf("inputmethod", "keyboard")

        /** Launchers/recents across common OEMs. */
        val HOME_PACKAGES = setOf(
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oneplus.launcher",
            "net.oneplus.launcher",
            "com.coloros.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.bbk.launcher2",
            "com.android.quickstep",
            "com.google.android.apps.quickstep",
            "com.android.quicksearchbox",
            "com.google.android.googlequicksearchbox",
        )

        private val HOME_HINTS = listOf("launcher", "quickstep")

        private val IME_PACKAGES = setOf(
            "com.google.android.inputmethod.latin",
            "com.android.inputmethod.latin",
            "com.android.inputmethod.pinyin",
            "com.samsung.android.honeyboard",
        )

        private fun isIme(pkg: String): Boolean =
            pkg in IME_PACKAGES ||
                IME_HINTS.any { pkg.contains(it, ignoreCase = true) } ||
                pkg.endsWith(".ime") ||
                pkg.contains(".ime.")

        /**
         * @param className the window's class name (AccessibilityEvent.className).
         *   Needed to tell our own *activities* (the detox app itself, which must
         *   release the blocker) from our own *overlay window* (the blocker, whose
         *   window-state event must never dismiss itself).
         */
        fun classify(
            pkg: String,
            ownPackage: String,
            className: String? = null,
        ): ForegroundSurface = when {
            // Our own package: only our Activities count as "the detox app opened".
            // Our overlay window reports a non-app class name, so its own event
            // falls through to TRANSIENT and never tears the blocker down.
            pkg == ownPackage && className != null && className.startsWith(ownPackage) -> OWN
            pkg == ownPackage -> TRANSIENT
            pkg in TRANSIENT_PACKAGES || isIme(pkg) -> TRANSIENT
            pkg in HOME_PACKAGES || HOME_HINTS.any { pkg.contains(it, ignoreCase = true) } -> HOME
            else -> APP
        }
    }
}
