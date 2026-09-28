package com.example.sonder.ui.kit

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.FocusRing
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import kotlin.math.ceil

/**
 * design_v3.md §5 frame language, as one primitive: a hard offset shadow (no blur,
 * no soft elevation) and optional 4dp stepped corner pixels cut out of the fill.
 * Everything framed in the app draws through this, so the shadow and corner grammar
 * stay identical on every surface.
 *
 * The shadow sits under the element, so a caller that wants the frame to occupy the
 * shadow's footprint adds [shadowOffset] as end/bottom spacing — [PixelPanel] does.
 */
fun Modifier.pixelShadow(
    offset: Dp = PixelSpace.Tight,
    color: Color = PixelPalette.ShadowPanel,
): Modifier = drawBehind {
    val o = offset.toPx()
    if (o > 0f) {
        drawRect(color = color, topLeft = Offset(size.width, o), size = size.copy(width = o))
        drawRect(color = color, topLeft = Offset(o, size.height), size = size.copy(height = o))
    }
}

/** Nicks each fill corner with a 4dp square (v3 "stepped corners"); call inside a clip. */
fun Modifier.pixelSteppedCorners(
    step: Dp = PixelSpace.Tight,
    cutColor: Color = PixelPalette.Bg,
): Modifier = drawBehind {
    val s = ceil(step.toPx()).toInt().toFloat()
    if (s <= 0f) return@drawBehind
    val w = size.width
    val h = size.height
    drawRect(cutColor, Offset(0f, 0f), androidx.compose.ui.geometry.Size(s, s))
    drawRect(cutColor, Offset(w - s, 0f), androidx.compose.ui.geometry.Size(s, s))
    drawRect(cutColor, Offset(0f, h - s), androidx.compose.ui.geometry.Size(s, s))
    drawRect(cutColor, Offset(w - s, h - s), androidx.compose.ui.geometry.Size(s, s))
}

/** Extra padding a stepped element needs so content clears its nicks (4dp per side). */
val SteppedCornerInset = PixelSpace.Tight

/**
 * v3 §5 "focused frame": a 2dp ring inset 4dp from the border, drawn behind the content
 * so focus never changes a control's layout — the keyboard/switch-access focus
 * treatment shared by every interactive kit control.
 */
internal fun Modifier.pixelFocusRing(visible: Boolean): Modifier = drawBehind {
    if (visible) {
        val inset = PixelSpace.Tight.toPx()
        val stroke = PixelSpace.Stroke.toPx()
        drawRect(
            color = FocusRing,
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2, size.height - inset * 2),
            style = Stroke(width = stroke),
        )
    }
}
