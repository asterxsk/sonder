package com.example.sonder.ui.kit

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale

/**
 * The button an act lands in: it fills left-to-right over [durationMillis] in [fillColor],
 * and [onSwept] runs when the fill reaches the far edge.
 *
 * This is the *second* half of a two-press control, not the wait itself — the wait is
 * [PixelHoldButton]'s fill, which the press can cancel. Nothing here is cancellable and
 * nothing here can be undone: the fill is the write being acknowledged, so it plays on the
 * control the user pressed, in that control's own colour (green for saved, red for removed),
 * and the screen leaves when it lands. Greyed-out text would be the alternative, and a
 * confirmation the user cannot see is not one.
 *
 * Stepped like every other motion in the app, so the fill advances in whole frames rather
 * than sliding, and non-interactive: this is a state, not a control — pressing it again must
 * not queue the act twice.
 */
@Composable
fun PixelSweepButton(
    text: String,
    fillColor: Color,
    onSwept: () -> Unit,
    modifier: Modifier = Modifier,
    durationMillis: Int = PixelMotion.WinMillis,
    /** Read out when the screen is not; the fill itself says nothing. */
    description: String = text,
) {
    val progress = remember { Animatable(0f) }

    // One effect, one landing: the fill is animated to exactly 1f and the act it stands for
    // is committed once, from the same coroutine that drew it.
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMillis, easing = PixelMotion.Stepped),
        )
        onSwept()
    }

    Box(
        modifier = modifier
            .heightIn(min = PixelSpace.Target)
            .background(PixelPalette.Surface)
            .border(PixelSpace.Stroke, fillColor)
            .clipToBounds()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.value)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .background(fillColor.copy(alpha = 0.35f)),
        )
        Text(
            text = text,
            style = PixelTypeScale.Button,
            fontFamily = PixelFont,
            color = fillColor,
            modifier = Modifier.padding(horizontal = PixelSpace.Snug, vertical = PixelSpace.Base),
        )
    }
}
