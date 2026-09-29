package com.example.sonder.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.PixelTab

/** Dock bar height. Shared with [PixelAppScaffold] so content padding clears the bar. */
internal val PixelDockHeight = 64.dp

/**
 * design_v3.md §15: the dock floats as a pill rather than sitting flush at the screen
 * edge, so it keeps this gap to the sides, the gesture area, and the content above it.
 * [PixelAppScaffold] adds it both to the dock's own inset padding and to the content's
 * bottom padding, so the pill never eats a row and a row never hides behind the pill.
 */
internal val PixelDockMargin = 16.dp

/**
 * design_v3.md §15: the pill corner radius — half of [PixelDockHeight], so the two ends
 * are true semicircles and not a rounded rectangle that reads as a misshapen bar. This
 * is the one deliberate local exception to the zero-radius rule in theme/PixelShapes;
 * it is a choice about the dock, not a theme change.
 */
internal val PixelDockRadius = 32.dp

/** One shape for the pill body and its active segment, so both curve identically. */
private val DockShape = RoundedCornerShape(PixelDockRadius)

/**
 * design_v3.md §15: bottom navigation is a console dock that floats as a rounded pill —
 * the one local exception to the pixel world's zero-radius rule. The active tab is a
 * square of raised panel inside the pill: amber glyph and label at full
 * [PixelPalette.Primary] against it, inactive items at TextSoft so
 * labels stay readable (≥7:1) without competing with the active amber. Pressed = 2px
 * translate, pixel-style, on the whole segment. Every tab is a real selected Tab role
 * with the shared keyboard/switch-access focus ring; [PixelDockHeight] tall so the
 * gesture area never eats taps. The fixed tab order, glyphs, and labels come from
 * [PixelTab], not a rebuilt list.
 */
@Composable
fun PixelDock(
    selected: PixelTab,
    onSelect: (PixelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PixelDockHeight)
            .drawBehind {
                // pixelShadow draws its hard-offset ground as straight right/bottom
                // strips, which would poke square corners out from under a rounded pill.
                // Same grammar — a solid ShadowPanel ground offset by that same strip,
                // no blur — drawn as one rounded ground the pill sits on, so only its
                // rounded right/bottom edge shows and the corners stay round.
                val o = PixelSpace.Tight.toPx()
                drawRoundRect(
                    color = PixelPalette.ShadowPanel,
                    topLeft = Offset(o, o),
                    size = size,
                    cornerRadius = CornerRadius(PixelDockRadius.toPx()),
                )
            }
            .background(PixelPalette.Surface, DockShape)
            .border(PixelSpace.Stroke, PixelPalette.Border, DockShape),
    ) {
        PixelTab.entries.forEach { tab ->
            // The active ground is a square on purpose: §15 rounds the pill, not the key
            // inside it, so the two shapes stay legible as two shapes.
            val active = tab == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            // §18: same 2-frame quantised press as PixelButton and PixelTabs.
            val pressOffset by animateDpAsState(
                targetValue = if (pressed) PixelSpace.Stroke else 0.dp,
                animationSpec = tween(
                    durationMillis = PixelMotion.PressMillis,
                    easing = PixelMotion.Stepped,
                ),
                label = "pixelDockPress",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .offset(y = pressOffset)
                    // Inset the segment from the pill's edge, leaving a 48dp [PixelSpace.Target]
                    // tall segment for the active tab to frame without touching the pill.
                    .padding(vertical = PixelSpace.Snug, horizontal = PixelSpace.Tight)
                    .then(
                        if (active) {
                            // A square, quietly-lit ground rather than an amber frame. The
                            // pill is already the loudest shape on screen; outlining a
                            // segment inside it put a second outline in the same object.
                            // Panel on Surface is a small step, so the selection reads as
                            // a key that is lit, and the amber type carries the rest.
                            Modifier.background(PixelPalette.Panel, RectangleShape)
                        } else {
                            Modifier
                        },
                    )
                    .pixelFocusRing(visible = focused)
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = interaction,
                        indication = null,
                    ) { onSelect(tab) }
                    // One merged node per tab: the label is the readable name, so the
                    // glyph is never spelled out as a stray character, and `selectable`
                    // supplies the selected state.
                    .semantics(mergeDescendants = true) { contentDescription = tab.label },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    androidx.compose.material3.Text(
                        text = tab.glyph,
                        style = PixelTypeScale.NavGlyph,
                        // Each tab sets its own, because the symbol font's characters fill
                        // their em boxes by very different amounts — §15.
                        fontSize = tab.glyphSize,
                        color = if (active) PixelPalette.Primary else TextSoft,
                    )
                    androidx.compose.material3.Text(
                        text = tab.label,
                        style = MonoTypeScale.NavLabel,
                        color = if (active) PixelPalette.Primary else TextSoft,
                    )
                }
            }
        }
    }
}
