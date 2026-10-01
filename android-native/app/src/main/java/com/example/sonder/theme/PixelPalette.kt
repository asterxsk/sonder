package com.example.sonder.theme

import androidx.compose.ui.graphics.Color

/**
 * Sonder Pixel UI v3 palette — tokens mirror design_v3.md §3 exactly.
 * Amber is the brand accent; blue/purple never compete with it.
 */
object PixelPalette {
    val Bg = Color(0xFF0B0906)          // deepest background
    val Surface = Color(0xFF15100B)     // normal panels
    val Panel = Color(0xFF221A12)       // raised panels
    val Border = Color(0xFFA68A52)      // structural border
    val BorderDark = Color(0xFF51452F)  // secondary frame
    val Primary = Color(0xFFFFB827)     // main action
    val PrimaryDark = Color(0xFFC88A16) // pixel shadow
    val Text = Color(0xFFEAD7A1)        // warm primary text
    // v3 §3 pins this at #7F765F, which measures 3.9:1 on Panel and 4.3:1 on Surface —
    // under the 4.5:1 body-text floor, and it is used for reading copy (onboarding
    // reassurance, ghost-step titles, stats labels), not decoration. Lifted in the same
    // hue family: 4.5:1 on Panel, 5.0:1 on Surface, 5.3:1 on Bg, still clearly quieter
    // than Text and TextSoft. docs/design/design_v3.md §3 carries the new value.
    val Muted = Color(0xFF8A8168)       // supporting text
    val Success = Color(0xFF22C55E)     // access granted
    val SuccessDark = Color(0xFF147D3C)
    val Danger = Color(0xFFEF4444)      // out of time
    val DangerDark = Color(0xFF9C2828)
    val Info = Color(0xFF5A9AC8)        // informational
    val Purple = Color(0xFF8B5CF6)      // special state only
    val CardFace = Color(0xFFEADFC7)    // playing card face (readability first)
    val CardInk = Color(0xFF18130E)     // playing card ink
    val ShadowPanel = Color(0xFF332816) // hard offset shadow for panels

    /**
     * The chips, one hue per stake, following the ordinary casino denomination ladder —
     * blue is the ten, orange the twenty, green above it, purple the top chip. See
     * [com.example.sonder.ui.kit.ChipTier] for which stake wears which.
     *
     * Cream is the single insert colour across every chip and every state. It is deliberately
     * not a tier colour: the inserts are what carries the chip's shape once the chip is
     * greyed at an empty bank, and if they dimmed with the tier a dead chip would be a smudge
     * with nothing to read.
     *
     * Orange rather than the literal yellow of a $20 chip: the gate's own action button is
     * this palette's amber, and a yellow chip sitting on it separates by almost nothing.
     */
    val ChipInsert = Color(0xFFF7ECCF)  // the eight edge inserts, on every chip
    val ChipTen = Color(0xFF3774C4)     // 02:00 — the $10 chip
    val ChipTwenty = Color(0xFFE2762B)  // 05:00 — the $20 chip
    val ChipTwentyFive = Color(0xFF2FA05E) // 10:00 — the $25 chip
    val ChipHigh = Color(0xFF8B5CF6)    // ALL IN — the top chip
}
