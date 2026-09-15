package com.example.sonder.ui.kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale

enum class PixelButtonStyle { PRIMARY, SECONDARY, SUCCESS, DANGER }

/**
 * design_v3.md §6 buttons: amber fill + dark text + hard shadow; pressed = translate 4px,
 * remove shadow. Secondary = dark panel + gold border. 48dp min touch target (§19).
 */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PixelButtonStyle = PixelButtonStyle.PRIMARY,
    enabled: Boolean = true,
    minHeight: Dp = 48.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val (fill, border, textColor, shadow) = when (style) {
        PixelButtonStyle.PRIMARY -> Tetrad(PixelPalette.Primary, PixelPalette.Primary, PixelPalette.Bg, PixelPalette.PrimaryDark)
        PixelButtonStyle.SECONDARY -> Tetrad(PixelPalette.Surface, PixelPalette.Border, PixelPalette.Text, PixelPalette.BorderDark)
        PixelButtonStyle.SUCCESS -> Tetrad(PixelPalette.Success, PixelPalette.Success, PixelPalette.Bg, PixelPalette.SuccessDark)
        PixelButtonStyle.DANGER -> Tetrad(PixelPalette.Danger, PixelPalette.Danger, PixelPalette.Bg, PixelPalette.DangerDark)
    }

    val offset = if (pressed) 4.dp else 0.dp
    val showShadow = !pressed

    Box(
        modifier = modifier
            .heightIn(min = minHeight)
            .offset(x = offset, y = offset)
            .background(if (enabled) fill else PixelPalette.Panel)
            .border(2.dp, if (enabled) border else PixelPalette.BorderDark)
            .padding(end = if (showShadow) 4.dp else 0.dp, bottom = if (showShadow) 4.dp else 0.dp)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                text = text,
                style = PixelTypeScale.Button,
                fontFamily = PixelFont,
                color = if (enabled) textColor else PixelPalette.Muted,
            )
        }
    }
}

private data class Tetrad(val fill: Color, val border: Color, val text: Color, val shadow: Color)
