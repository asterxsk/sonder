package com.example.sonder.domain

/**
 * How much of a target is gated.
 *
 * A target used to be all-or-nothing: enabling a package put the whole app behind the
 * table. That is the wrong shape for the two apps people actually lose time to —
 * Instagram and YouTube are mostly a feed and a search box, and the minutes disappear
 * into Reels and Shorts, which live *inside* the same activity. Gating the whole app to
 * reach the shorts feed blocks the messaging and the tutorial the user came for, and a
 * gate that is wrong about what it is protecting is a gate the user switches off.
 *
 * [SHORTS_ONLY] gates the app only while the shorts/reels surface is the one on screen,
 * so the rest of it stays usable. Which surface that is, and for which packages, is
 * [ShortsCatalog]'s business; this type only names the choice.
 */
enum class BlockScope {
    /** Every window of the app is a target. The behaviour every target had before this. */
    WHOLE_APP,

    /** Only the app's short-form video surface is a target; the rest of the app passes. */
    SHORTS_ONLY,
    ;

    companion object {
        /** Stored form of [WHOLE_APP], and what any unrecognised or missing value reads as. */
        const val STORED_WHOLE_APP = "WHOLE_APP"

        /** Stored form of [SHORTS_ONLY]. */
        const val STORED_SHORTS_ONLY = "SHORTS_ONLY"

        /**
         * Read a stored column back.
         *
         * Raw-string comparison, never `valueOf`: the column is user-visible state written
         * by builds that may not agree on the set, and a stored value this build does not
         * recognise must fall back to the scope that gates the whole app. That direction is
         * deliberate — an unknown value means a newer build wrote something this one cannot
         * interpret, and of the two possible mistakes, gating more than asked is the safe
         * one. `null` is a row written before the column existed, and is [WHOLE_APP] for
         * the same reason: that is what every row meant at the time.
         */
        fun fromStored(raw: String?): BlockScope = when (raw) {
            STORED_SHORTS_ONLY -> SHORTS_ONLY
            else -> WHOLE_APP
        }
    }

    /** The value to write to the column. */
    val stored: String
        get() = when (this) {
            WHOLE_APP -> STORED_WHOLE_APP
            SHORTS_ONLY -> STORED_SHORTS_ONLY
        }
}
