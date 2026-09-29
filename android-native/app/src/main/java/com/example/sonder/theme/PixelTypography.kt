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
 * design_v3.md §4 scale (px → sp 1:1 on mdpi baseline), with the two properties the doc
 * leaves to the implementation and that pixel type cannot go without.
 *
 * **Line height.** Press Start 2P carries no vertical metrics a Compose `TextStyle` can
 * inherit usefully, so every style set only a size and took the platform default — which
 * left multi-line copy at the 11sp section title crowding its own rows and the 30sp timer
 * sitting loose in its frame. Each style now states its own, at roughly 1.4× for the
 * small pixel roles and 1.15× for the timer, whose frame is sized to it.
 *
 * **Tracking.** A pixel face is drawn on a fixed grid, so its sidebearings are whole
 * pixels rather than optical; at 9–11sp the glyphs touch. A half-pixel of tracking opens
 * the word without breaking the grid.
 *
 * The floor is deliberate: the pixel face is never set below 10sp. §4's range allows 9sp
 * for buttons, badges, and navigation, and 9sp Press Start 2P is a legibility failure on
 * a real handset — those three roles sit at the top of their allowed range instead.
 */
object PixelTypeScale {
    val Wordmark = TextStyle(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    )
    val ScreenTitle = TextStyle(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )
    val SectionTitle = TextStyle(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.5.sp,
    )
    val Button = TextStyle(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
    )
    val Timer = TextStyle(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 34.sp,
    )
    val Badge = TextStyle(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
    )
    val Nav = TextStyle(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 15.sp,
    )

    /**
     * The dock's tab glyph. No font family on purpose: `⌂ ◎ ▥ ⚙` are symbols the pixel
     * face has no cut of, so they render from the platform's symbol font as they always
     * have — this only makes them big enough to be the dock's icon rather than a
     * decorative fleck above the label.
     */
    val NavGlyph = TextStyle(
        fontSize = 26.sp,
        lineHeight = 28.sp,
    )

    /**
     * A row's action glyph (`✎ ✕`) — same reasoning as [NavGlyph]: a symbol-font
     * character, sized to be the control's mark inside its 48dp frame rather than a
     * stray character floating in an empty box.
     */
    val RowGlyph = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
    )
    val CardFace = TextStyle(
        fontFamily = PixelFont,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 22.sp,
    )
}

/** Body/metadata styles use the readable mono. */
object MonoTypeScale {
    val Body = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )
    val Metadata = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
    val PackageId = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
    )

    /**
     * The dock's tab label. Mono rather than the pixel face for two reasons: the label is
     * sentence case, which the pixel face has no lowercase cut for, and §4's floor keeps
     * the pixel face at 10sp and above — a smaller pixel label would break that rule
     * rather than bend it. Under a large glyph the label is the caption, not the icon.
     */
    val NavLabel = TextStyle(
        fontFamily = MonoFont,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 13.sp,
    )
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
