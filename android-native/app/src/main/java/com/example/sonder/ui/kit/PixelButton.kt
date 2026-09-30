package com.example.sonder.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.OnPrimaryMuted
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale

enum class PixelButtonStyle { PRIMARY, SECONDARY, SUCCESS, DANGER }

private data class Tetrad(val fill: Color, val border: Color, val text: Color, val shadow: Color)

private fun colors(style: PixelButtonStyle, enabled: Boolean) = when (style) {
    PixelButtonStyle.PRIMARY -> if (enabled) {
        Tetrad(PixelPalette.Primary, PixelPalette.Primary, PixelPalette.Bg, PixelPalette.PrimaryDark)
    } else {
        Tetrad(PixelPalette.Panel, PixelPalette.BorderDark, PixelPalette.Muted, Color.Transparent)
    }
    PixelButtonStyle.SECONDARY -> if (enabled) {
        Tetrad(PixelPalette.Surface, PixelPalette.Border, PixelPalette.Text, PixelPalette.BorderDark)
    } else {
        Tetrad(PixelPalette.Panel, PixelPalette.BorderDark, PixelPalette.Muted, Color.Transparent)
    }
    PixelButtonStyle.SUCCESS -> if (enabled) {
        Tetrad(PixelPalette.Success, PixelPalette.Success, PixelPalette.Bg, PixelPalette.SuccessDark)
    } else {
        Tetrad(PixelPalette.Panel, PixelPalette.BorderDark, PixelPalette.Muted, Color.Transparent)
    }
    PixelButtonStyle.DANGER -> if (enabled) {
        Tetrad(PixelPalette.Danger, PixelPalette.Danger, PixelPalette.Bg, PixelPalette.DangerDark)
    } else {
        Tetrad(PixelPalette.Panel, PixelPalette.BorderDark, PixelPalette.Muted, Color.Transparent)
    }
}

/**
 * design_v3.md §6 buttons: amber fill + dark text + hard shadow; pressed = translate 4px,
 * remove shadow, and step the label to OnPrimaryMuted so the action stays legible mid-press.
 * Disabled = dead panel with muted text and no shadow — unmistakably off.
 * 48dp min touch target (§19); exposes a real Button role for TalkBack; draws the
 * keyboard/switch-access focus ring inside the frame (2dp FocusRing inset 4dp).
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PixelButtonStyle = PixelButtonStyle.PRIMARY,
    enabled: Boolean = true,
    minHeight: Dp = PixelSpace.Target,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()

    // Memoized: the four colours are picked from a fixed palette by (style, enabled), so
    // rebuilding the Tetrad on every recomposition allocated a data class for nothing.
    val tetrad = remember(style, enabled) { colors(style, enabled) }
    val fill = tetrad.fill
    val border = tetrad.border
    val textColor = tetrad.text

    // §18: the press is 2–4 discrete frames — not an instant jump, and not a smooth
    // slide. Stepped easing quantises the 90ms travel to four frames so the button
    // lands on the shadow's footprint the way a sprite's press frame does.
    val offset by animateDpAsState(
        targetValue = if (pressed) PixelSpace.Tight else 0.dp,
        animationSpec = tween(
            durationMillis = PixelMotion.PressMillis,
            easing = PixelMotion.Stepped,
        ),
        label = "pixelButtonPress",
    )
    val showShadow = !pressed && enabled

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .offset(x = offset, y = offset)
            .background(fill)
            .border(PixelSpace.Stroke, border)
            .pixelFocusRing(visible = focused)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = PixelSpace.Room, vertical = PixelSpace.Base)
                // Shadow padding is OUTSIDE the text box; clickable covers the
                // whole button including the shadow strip, so edge taps register.
                .padding(
                    end = if (showShadow) PixelSpace.Tight else 0.dp,
                    bottom = if (showShadow) PixelSpace.Tight else 0.dp,
                ),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                text = text,
                style = PixelTypeScale.Button,
                fontFamily = PixelFont,
                color = when {
                    !enabled -> PixelPalette.Muted
                    pressed && style == PixelButtonStyle.PRIMARY -> OnPrimaryMuted
                    else -> textColor
                },
            )
        }
    }
}
