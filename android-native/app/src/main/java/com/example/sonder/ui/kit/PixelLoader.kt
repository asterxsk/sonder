package com.example.sonder.ui.kit

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace

/**
 * The one pixel loading treatment (design_v3 §18: discrete steps, never a smooth
 * spinner): three blocks fill left to right in hard frames. Home, Targets, Stats
 * and the root gate all wait through this, so initializing never reads as frozen
 * and never off-world.
 */
@Composable
fun PixelLoader(
    modifier: Modifier = Modifier,
    blockCount: Int = 3,
    blockSize: Dp = 14.dp,
    cycleMillis: Int = 900,
) {
    val transition = rememberInfiniteTransition(label = "pixelLoader")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (blockCount + 1).toFloat(),
        animationSpec = infiniteRepeatable(
            // Linear on purpose: the fill IS the frame counter, so easing it would fight
            // the loop. Every other tween in the app uses PixelMotion.Stepped.
            animation = tween(durationMillis = cycleMillis, easing = PixelMotion.Continuous),
        ),
        label = "phase",
    )

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(PixelSpace.Snug)) {
        repeat(blockCount) { index ->
            Box(
                modifier = Modifier
                    .size(blockSize)
                    .background(if (index < phase) PixelPalette.Primary else PixelPalette.BorderDark),
            )
        }
    }
}
