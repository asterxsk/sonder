package com.example.sonder.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.sonder.R

/** Pixel display font (Press Start 2P, SIL OFL) — wordmark, titles, buttons, timers, badges. */
val PixelFont = FontFamily(
    Font(R.font.press_start_2p, FontWeight.Normal),
    Font(R.font.press_start_2p, FontWeight.Bold),
)

/** Functional mono (DM Mono, SIL OFL) — descriptions, package IDs, metadata. */
val MonoFont = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

/**
 * design_v3.md §4 scale (px → sp 1:1 on mdpi baseline):
 * wordmark 28–32, screen title 16–20, section 10–12, button 9–11, body 13–15, metadata 9–11.
 * Pixel font below 10sp gets DM Mono instead where legibility matters (§ risk note).
 */
object PixelTypeScale {
    val Wordmark = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 28.sp)
    val ScreenTitle = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 16.sp)
    val SectionTitle = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 11.sp)
    val Button = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 10.sp)
    val Timer = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 30.sp)
    val Badge = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 9.sp)
    val Nav = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 9.sp)
    val CardFace = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Normal, fontSize = 18.sp)
}

/** Body/metadata styles use the readable mono. */
object MonoTypeScale {
    val Body = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 14.sp)
    val Metadata = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 11.sp)
    val PackageId = TextStyle(fontFamily = MonoFont, fontWeight = FontWeight.Normal, fontSize = 10.sp)
}

/** Minimal Material mapping so any M3 component that sneaks in stays on-brand. */
val PixelTypography = Typography(
    displayLarge = PixelTypeScale.Wordmark,
    titleLarge = PixelTypeScale.ScreenTitle,
    titleMedium = PixelTypeScale.SectionTitle,
    labelLarge = PixelTypeScale.Button,
    bodyLarge = MonoTypeScale.Body,
    bodyMedium = MonoTypeScale.Metadata,
    bodySmall = MonoTypeScale.PackageId,
)
