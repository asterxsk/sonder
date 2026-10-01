package com.example.sonder.ui.kit

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelMotion

/**
 * The frames in one pass, and how far the chip moves between them.
 *
 * Neither number is free, and the constraint is [ChipIcon]'s own geometry: the chip's rim
 * carries **eight inserts evenly spaced around it**, so half and quarter turns come out
 * *bit-for-bit identical*. Measured against the drawn grid, a 90° rotation differs in 0 of
 * its 576 cells — so a quarter-turn loader, which is the obvious thing to write and was the
 * first version of this, renders the same image on every frame and looks completely frozen
 * rather than subtly wrong.
 *
 * What is left is the angle *modulo* 45°, and that is what these two numbers choose: five
 * frames stepping 9° each, at 0°, 9°, 18°, 27°, 36°. All five are visibly distinct (9°, 18°,
 * 27° and 36° differ from the upright chip in 124, 200, 204 and 108 of those 576 cells).
 *
 * The wrap is not a snap. A 45° rotation maps the inserts back onto the inserts and differs
 * in only 40 cells, so the step from the last frame to the first is *smaller* than an
 * ordinary frame step — the loop closes without a visible reset.
 *
 * Five is §18's frame idiom and the most that keeps the step legible: more frames would step
 * less far each time, and much past 5° a rotation this small stops reading as movement.
 */
private const val SpinFrames = 5
private const val DegreesPerFrame = 9f

/**
 * The one pixel loading treatment: a chip, stepping round a quarter turn at a time.
 *
 * It is the app's own chip rather than a generic spinner, because the chip is what the gate
 * counts in — a loading screen that showed a clock or a bar would be the one piece of the UI
 * not speaking the table's language. Drawn through [ChipIcon], so it is the same art the
 * stakes are bet with, at the same palette, and it cannot drift from them.
 *
 * **Stepped, not smooth.** §18 forbids continuous motion, and every other transition in the
 * app is quantised to [PixelMotion.Steps] frames; a chip that span smoothly would be the one
 * object in the interface that eases, and it would read as a different app. The step is what
 * makes it legible as *frames* rather than as a slow rotation: the eye catches each one and
 * reads the loop as deliberate. [PixelMotion.Continuous] is used for the tween's own clock
 * only, so the frames land evenly apart — the quantisation is done by the angle being floored
 * to [DegreesPerFrame], not by the easing curve.
 *
 * The tile is marked "Loading" for TalkBack: the chip is a decorative mark standing in for a
 * screen that has not answered yet, and a screen reader has nothing else to read on it.
 */
@Composable
fun PixelLoader(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
) {
    val transition = rememberInfiniteTransition(label = "pixelLoader")
    val frame by transition.animateFloat(
        initialValue = 0f,
        targetValue = SpinFrames.toFloat(),
        animationSpec = infiniteRepeatable(
            // Linear on purpose: this is a frame counter rather than an angle being eased
            // between two values, and the whole number is what is drawn. Floored below, so
            // the value the chip is drawn at is always one of [SpinFrames] — a curve here
            // would only redistribute frames that are discarded anyway.
            animation = tween(
                durationMillis = SpinCycleMillis,
                easing = PixelMotion.Continuous,
            ),
        ),
        label = "frame",
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                // The step is taken from the animated clock and *not* eased into: `frame`
                // runs 0..[SpinFrames] over the cycle and each whole number is one step, so
                // the chip is still for most of every frame's worth of time and jumps the
                // rest. A smooth curve here would give it gentle ramps instead of steps.
                rotationZ = (frame.toInt() % SpinFrames) * DegreesPerFrame
            }
            .semantics { contentDescription = "Loading" },
        contentAlignment = Alignment.Center,
    ) {
        ChipIcon(tier = ChipTier.TEN, state = ChipState.READY, size = size)
    }
}

/**
 * How long one pass through the [SpinFrames] takes.
 *
 * Slow enough that each step is separately readable — a fifth of a second per frame, which is
 * around the speed a pixel sprite is animated at — and short enough that a load finishing in
 * half a second still shows motion rather than a still frame.
 */
private const val SpinCycleMillis = 1_000
