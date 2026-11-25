package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale

/**
 * HeroUI's Chip: a small, self-contained status token that sits inside a card header or a
 * list row without becoming a second card.
 *
 * The pixel grammar makes one deliberate departure from HeroUI's stock chip. HeroUI fills
 * a chip with a tinted ground and a rounded capsule; this world has no rounded shape
 * outside the dock and no tints that would separate cleanly from the panel behind them. So
 * a chip here is a *framed label*: the screen's own ground inside, the tone carried by the
 * 2dp frame and the label, and nothing else. That is also why [variant] exists — a
 * [SOLID] chip uses the tone as ink on a filled block for the one loud case, and an
 * [OUTLINE] chip is the quiet default the status badges have always used.
 */
enum class PixelChipVariant { SOLID, OUTLINE, SOFT }

/**
 * How far an outlined chip's frame steps back from its label. It has to stay visible on a
 * Surface ground (it is a graphic, not text, so it is held to the 3:1 non-text floor rather
 * than 4.5:1) while reading clearly quieter than the card border it sits inside.
 */
private const val OutlineFrameAlpha = 0.55f

/** A chip's meaning. Colour never carries it alone: every chip prints its own word. */
enum class PixelChipTone(internal val color: Color) {
    PRIMARY(PixelPalette.Primary),
    SUCCESS(PixelPalette.Success),
    DANGER(PixelPalette.Danger),
    INFO(PixelPalette.Info),
    NEUTRAL(PixelPalette.Muted),
}

/**
 * One chip.
 *
 * [glyph] is optional and is drawn as a stamp inside the frame — the check, the lock, the
 * spade — but a caller that has no glyph must still be readable, so the label is required
 * and the glyph only reinforces it.
 *
 * Chips are not controls and never carry a click: HeroUI makes chips interactive in some
 * configurations, and this app has one place where a tappable token would make sense
 * (filtering the stats ledger), which is better served by [PixelTabs] than by a chip that
 * secretly behaves like a button.
 */
@Composable
fun PixelChip(
    label: String,
    modifier: Modifier = Modifier,
    tone: PixelChipTone = PixelChipTone.NEUTRAL,
    variant: PixelChipVariant = PixelChipVariant.OUTLINE,
    glyph: String? = null,
) {
    val fill = when (variant) {
        PixelChipVariant.SOLID -> tone.color
        PixelChipVariant.SOFT -> PixelPalette.Panel
        PixelChipVariant.OUTLINE -> PixelPalette.Surface
    }
    val ink = when (variant) {
        // Dark ink on a bright block, so a solid chip stays legible at the pixel face's
        // weight — the same relationship the primary button has with its label.
        PixelChipVariant.SOLID -> PixelPalette.Bg
        PixelChipVariant.SOFT, PixelChipVariant.OUTLINE -> tone.color
    }

    Row(
        modifier = modifier
            .background(fill)
            .border(
                width = PixelSpace.Stroke,
                // An outlined chip's frame is a *qualifier*, not an alarm: at full tone it
                // was as heavy as the card's own border and every one of three LOCKED chips
                // on a list read as its own warning. The label keeps the full tone; only the
                // box around it steps back, which is the difference between a status and a
                // siren. A solid chip is the loud case and keeps its full tone.
                color = if (variant == PixelChipVariant.SOLID) {
                    tone.color
                } else {
                    tone.color.copy(alpha = OutlineFrameAlpha)
                },
            )
            .padding(horizontal = PixelSpace.Snug, vertical = PixelSpace.Stroke),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PixelSpace.Tight),
    ) {
        androidx.compose.material3.Text(
            // One string, not two Text nodes: a glyph and a label separated in layout can
            // wrap between them, and the whole point of a chip is that it never does.
            text = glyph?.let { "$it $label" } ?: label,
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            maxLines = 1,
            softWrap = false,
            color = ink,
        )
    }
}
