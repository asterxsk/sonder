package com.example.sonder.ui.kit

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelPalette
import kotlinx.coroutines.delay

/**
 * The house cat: three lines of ASCII, sitting under the console's status line.
 *
 * It is the one piece of the UI that is not a control or a readout, and it is drawn in the
 * functional mono face rather than the pixel face because ASCII art is a fixed-grid drawing
 * — the pixel face would re-space the glyphs and break the picture.
 *
 * Animated in the world's own terms: [PixelMotion]'s rule is discrete steps and no
 * interpolation, so the cat does not smoothly move, it *changes frames* — a blink, then a
 * tail flick, then back to sitting. Two frames of eyes and one of tail is all the motion it
 * needs, and anything longer than a held gaze stops reading as an animal and starts reading
 * as an animation playing at the user.
 */
@Composable
fun PixelCat(
    modifier: Modifier = Modifier,
    color: Color = PixelPalette.Primary,
    /** What a screen reader is told this is, or null to leave it out of the tree entirely. */
    description: String? = "A cat, watching the limits.",
) {
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(SitMillis)
            frame = BlinkFrame
            delay(BlinkMillis)
            frame = SitFrame
            // The tail goes a shorter beat after the eyes than the next blink is away, so the
            // two never land together and the loop does not read as one four-beat tic.
            delay(SitMillis / 3)
            frame = TailFrame
            delay(TailMillis)
            frame = SitFrame
        }
    }

    Text(
        text = Frames[frame],
        style = MonoTypeScale.Body,
        color = color,
        textAlign = TextAlign.Center,
        modifier = if (description == null) {
            modifier
        } else {
            modifier.semantics { contentDescription = description }
        },
    )
}

/** Sitting, eyes shut, tail up. Index order is the order the loop plays them in. */
private const val SitFrame = 0
private const val BlinkFrame = 1
private const val TailFrame = 2

/** Long enough to sit still between the two beats, short enough that the cat is alive. */
private const val SitMillis = 2_400L
private const val BlinkMillis = 140L
private const val TailMillis = 220L

/**
 * The three frames, written as one picture each rather than assembled from parts: a cat whose
 * ears and face are patched together from offsets is a cat that stops looking like one the
 * moment either side is edited.
 */
private val Frames = listOf(
    """
     /\_/\
    ( o.o )
     > ^ <
    """.trimIndent(),
    """
     /\_/\
    ( -.- )
     > ^ <
    """.trimIndent(),
    """
     /\_/\
    ( o.o )
     ~ ^ ~
    """.trimIndent(),
)
