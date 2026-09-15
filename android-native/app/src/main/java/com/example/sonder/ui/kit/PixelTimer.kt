package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale

/** Timer accent color: amber warning, green granted, red lockout (design_v3 §13). */
enum class TimerTone { WARNING, GRANTED, LOCKED }

/**
 * design_v3.md §13: the timer is a major branded element — pixel digits in a hard frame
 * with a caption. Digits colored by tone.
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
                .border(2.dp, PixelPalette.Border)
                .padding(horizontal = 16.dp, vertical = 10.dp),
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
            color = PixelPalette.Muted,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** The four canonical badges of design_v3 §14: ✓ GRANTED / ▣ LOCKED / ♠ PLAYING / ⌛ COOLDOWN. */
enum class BadgeTone(val glyph: String, val label: String, val color: Color) {
    GRANTED("✓", "GRANTED", PixelPalette.Success),
    LOCKED("▣", "LOCKED", PixelPalette.Danger),
    PLAYING("♠", "PLAYING", PixelPalette.Info),
    COOLDOWN("⌛", "COOLDOWN", PixelPalette.Primary),
}

@Composable
fun PixelStatusBadge(
    tone: BadgeTone,
    modifier: Modifier = Modifier,
    labelOverride: String? = null,
) {
    Row(
        modifier = modifier
            .background(PixelPalette.Surface)
            .border(2.dp, tone.color)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            text = "${tone.glyph} ${labelOverride ?: tone.label}",
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = tone.color,
        )
    }
}
