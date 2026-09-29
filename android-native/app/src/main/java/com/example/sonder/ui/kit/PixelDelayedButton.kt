package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale

/**
 * The pending action's countdown strip (design_v3 §13's hardware grammar). It takes
 * the place of the two row controls while a wait runs, so the hold is visible rather
 * than a frozen button — and it is itself the cancel target: a tap on it is how a
 * user backs out. [tone] colors the frame and readout by what is counting down, so an
 * amber EDIT never reads as the same thing as a red REMOVE. [contentDescription] must
 * say both what will happen and that tapping cancels, since the label alone ("REMOVE
 * 0:24") says neither well.
 */
@Composable
fun PixelDelayedButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = PixelPalette.Primary,
) {
    Box(
        modifier = modifier
            .heightIn(min = PixelSpace.Target)
            .background(PixelPalette.Surface)
            .pixelShadow()
            .pixelSteppedCorners()
            .border(PixelSpace.Stroke, tone)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = PixelSpace.Snug),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = tone,
        )
    }
}
