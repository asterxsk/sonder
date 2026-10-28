package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft

/** Timer accent color: amber warning, green granted, red for an empty bank (design_v3 §13). */
enum class TimerTone { WARNING, GRANTED, LOCKED }

/**
 * design_v3.md §13: the timer is a major branded element — pixel digits in a hard
 * shadowed frame with a caption. Digits colored by tone; the frame carries the app's
 * shadow + stepped-corner grammar so the countdown reads as hardware, not a label.
 */
@Composable
fun PixelTimer(
    timeText: String,
    caption: String,
    modifier: Modifier = Modifier,
    tone: TimerTone = TimerTone.WARNING,
) {
    val digitColor = when (tone) {
        TimerTone.WARNING -> PixelPalette.Primary
        TimerTone.GRANTED -> PixelPalette.Success
        TimerTone.LOCKED -> PixelPalette.Danger
    }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .background(PixelPalette.Surface)
                .pixelShadow()
                .pixelSteppedCorners()
                .border(PixelSpace.Stroke, digitColor)
                .padding(horizontal = PixelSpace.Room, vertical = PixelSpace.Snug),
        ) {
            androidx.compose.material3.Text(
                text = timeText,
                style = PixelTypeScale.Timer,
                fontFamily = PixelFont,
                color = digitColor,
            )
        }
        androidx.compose.material3.Text(
            text = caption,
            style = PixelTypeScale.Badge,
            color = TextSoft,
            modifier = Modifier.padding(top = PixelSpace.Snug),
        )
    }
}

/**
 * The four canonical badges of design_v3 §14: ✓ GRANTED / ▣ LOCKED / ♠ PLAYING / ⌛ COOLDOWN.
 * Each one names its [PixelChipTone] so the badge and the chip cannot drift apart — a
 * GRANTED badge is the success tone wherever it is drawn.
 */
enum class BadgeTone(
    val glyph: String,
    val label: String,
    internal val chip: PixelChipTone,
) {
    GRANTED("✓", "GRANTED", PixelChipTone.SUCCESS),
    LOCKED("▣", "LOCKED", PixelChipTone.DANGER),
    PLAYING("♠", "PLAYING", PixelChipTone.INFO),
    COOLDOWN("⌛", "COOLDOWN", PixelChipTone.PRIMARY),
}

/** The badge's colour, kept as a property so callers that tint something else can match it. */
val BadgeTone.color: Color get() = chip.color

/**
 * The status badge, now drawn through [PixelChip] so the app has one status token rather
 * than two: the chip's frame, its badge type and its two-tone logic, with the badge's own
 * glyph and word supplied here.
 *
 * [labelOverride] exists for the callers whose state is not one of the four canonical ones
 * — a row that says SAVED rather than GRANTED — and keeps the glyph, because the glyph is
 * what makes a scan of the list possible.
 */
@Composable
fun PixelStatusBadge(
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
) {
    PixelChip(
        label = labelOverride ?: tone.label,
        glyph = tone.glyph,
        tone = tone.chip,
        variant = PixelChipVariant.OUTLINE,
        modifier = modifier,
    )
}
