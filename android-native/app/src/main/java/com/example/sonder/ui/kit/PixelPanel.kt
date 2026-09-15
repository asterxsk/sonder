package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.BoxScope
import com.example.sonder.theme.PixelPalette

/**
 * design_v3.md §5 frame language: 0px radius, 2–3px border, hard offset shadow,
 * no blur, no soft elevation. Stepped corner pixels are drawn via [PixelSteppedCorners].
 */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = PixelPalette.Border,
    fillColor: Color = PixelPalette.Surface,
    shadowColor: Color = PixelPalette.ShadowPanel,
    borderWidth: Dp = 2.dp,
    shadowOffset: Dp = 4.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(elevation = shadowOffset, shape = androidx.compose.ui.graphics.RectangleShape, ambientColor = shadowColor, spotColor = shadowColor)
            .background(fillColor)
            .border(borderWidth, borderColor)
            .padding(12.dp),
        content = content,
    )
}
