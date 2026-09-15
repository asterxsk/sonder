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
    val Muted = Color(0xFF7F765F)       // supporting text
    val Success = Color(0xFF22C55E)     // access granted
    val SuccessDark = Color(0xFF147D3C)
    val Danger = Color(0xFFEF4444)      // lockout
    val DangerDark = Color(0xFF9C2828)
    val Info = Color(0xFF5A9AC8)        // informational
    val Purple = Color(0xFF8B5CF6)      // special state only
    val CardFace = Color(0xFFEADFC7)    // playing card face (readability first)
    val CardInk = Color(0xFF18130E)     // playing card ink
    val ShadowPanel = Color(0xFF332816) // hard offset shadow for panels
}
