package com.example.sonder.ui.kit

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import kotlin.math.floor
import kotlinx.coroutines.delay

/**
 * A two-press button: the first press starts a [holdMillis] wait that fills the button
 * left-to-right, and only a press after that wait lands is the one that acts.
 *
 * The waits on the per-app settings screen are deliberate friction — a rule is easy to
 * change back but hard to notice changing — so the fill is what makes the wait legible: it
 * says both "this is happening" and "how much longer", and a tap while it is running cancels
 * the whole thing rather than committing anything. A button that merely froze for 30 seconds
 * would read as a hang.
 *
 * The fill is green by [fillColor] default because both waits here are the *second* half of a
 * destructive pair, and the completed state should look like permission rather than warning.
 *
 * @param text what the button reads before any press.
 * @param holdMillis how long the fill takes. The second press is only accepted once it has
 *   run out — pressing early cancels, and does not re-arm silently: the strip empties.
 * @param onComplete the acting press, once armed.
 * @param enabled false leaves the button inert in the IDLE state; a running wait is always
 *   cancellable, so this cannot strand a fill on screen.
 */
@Composable
fun PixelHoldButton(
    text: String,
    holdMillis: Long,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Frame and idle label colour; the fill has its own. */
    tone: Color = PixelPalette.Primary,
    fillColor: Color = PixelPalette.Success,
    armedText: String = "PRESS AGAIN",
) {
    var armed by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(0) }

    // The countdown is a single effect keyed on the run, so a cancel and a re-arm cannot
    // leave two clocks racing to set the same flags.
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var left = holdMillis
        while (left > 0) {
            remainingSeconds = ((left + 999) / 1000).toInt()
            delay(minOf(1_000L, left))
            left -= 1_000L
        }
        remainingSeconds = 0
        running = false
        armed = true
    }

    // Stepped, like every other motion in the app — but stepped to *this wait's* own length
    // rather than to PixelMotion's four frames. A thirty-second fill quantised to four steps
    // moves once every seven and a half seconds, so the strip sits empty through the whole
    // first quarter of the wait and reads as a button that is not filling at all; the second
    // press then arrives against a bar that was never seen to move. One step a second is
    // still discrete and still interpolates nothing, and it ticks in time with the countdown
    // written on the label beside it.
    val steps = ((holdMillis + 999L) / 1_000L).coerceAtLeast(1L).toInt()
    val fillEasing = remember(steps) {
        Easing { fraction -> floor(fraction * steps) / steps }
    }
    val progress by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (running) holdMillis.toInt() else 0,
            easing = fillEasing,
        ),
        label = "holdFill",
    )

    val label = when {
        armed -> armedText
        running -> "HOLD ${remainingSeconds}s  ·  TAP TO CANCEL"
        else -> text
    }

    Box(
        modifier = modifier
            .heightIn(min = PixelSpace.Target)
            .background(PixelPalette.Surface)
            .border(PixelSpace.Stroke, if (enabled || running) tone else PixelPalette.BorderDark)
            .clickable(
                role = Role.Button,
                enabled = enabled || running,
                onClick = {
                    when {
                        armed -> {
                            armed = false
                            onComplete()
                        }
                        // A running wait is cancelled by the same tap that started it, which
                        // is the only way out of a 30-second fill short of leaving the screen.
                        running -> running = false
                        else -> running = true
                    }
                },
            )
            .clipToBounds()
            .semantics {
                contentDescription = when {
                    armed -> "$armedText — press to confirm $text"
                    running -> "Waiting ${remainingSeconds} seconds; tap to cancel"
                    else -> text
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The fill. Measured against the button's own width, so it is a fraction of the real
        // control rather than of a fixed guess at one.
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(fillColor.copy(alpha = 0.35f)),
            )
        }
        Text(
            text = label,
            style = PixelTypeScale.Button,
            fontFamily = PixelFont,
            color = when {
                !enabled && !running -> PixelPalette.Muted
                armed -> fillColor
                running -> PixelPalette.Text
                else -> tone
            },
            modifier = Modifier.padding(horizontal = PixelSpace.Snug, vertical = PixelSpace.Base),
        )
    }
}
