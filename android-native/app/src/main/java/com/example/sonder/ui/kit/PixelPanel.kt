package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace

/**
 * design_v3.md §5 frame language, drawn through the shared primitives: 0px radius,
 * 2px border, hard offset shadow (no blur, no soft elevation) via [pixelShadow], and
 * 4dp stepped corners via [pixelSteppedCorners] — the corner pixels the v3 doc
 * specifies and the old implementation never drew.
 */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = PixelPalette.Border,
    fillColor: Color = PixelPalette.Surface,
    shadowColor: Color = PixelPalette.ShadowPanel,
    borderWidth: Dp = PixelSpace.Stroke,
    shadowOffset: Dp = PixelSpace.Tight,
    steppedCorners: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(fillColor)
            .pixelShadow(offset = shadowOffset, color = shadowColor)
            .border(borderWidth, borderColor)
            // Nicks overpaint the border corners, so corners read as steps, not holes.
            // Opting out has to drop the nicks as well as the inset: leaving them in place
            // while removing the padding the panel reserved for them is what cut the
            // corners off a panel whose caller had asked for square ones.
            .then(if (steppedCorners) Modifier.pixelSteppedCorners() else Modifier)
            .padding(PixelSpace.Base + (if (steppedCorners) SteppedCornerInset else 0.dp)),
        content = content,
    )
}
