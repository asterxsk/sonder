package com.example.sonder.ui.kit

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace

/**
 * The list a screen shows while it is reading the launcher, drawn as the rows it is
 * about to become: same frame, same icon slot, same two control frames. A spinner or a
 * "LOADING" panel in the middle of an empty screen tells the user to wait and tells them
 * nothing else; a skeleton tells them what is coming and holds its shape, so the rows
 * do not jump into place when they arrive.
 *
 * It is a placeholder, not a lie: every block is inert, unlabelled to accessibility (one
 * node per screen says "loading", [PixelSkeletonList] owns it), and the pulse is §18
 * motion — two hard frames, no shimmer travelling across the screen.
 */
@Composable
private fun skeletonPulse(): Float {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val level by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = SkeletonMillis, easing = PixelMotion.Stepped),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonLevel",
    )
    return level
}

/** One placeholder row, shaped like [TargetRow] down to its sizes. */
@Composable
private fun PixelSkeletonRow(level: Float) {
    // A two-stop mix between the two nearest grounds rather than an animated alpha: alpha
    // would let the row's own frame show through the placeholder and read as a lighter
    // row, not as one that has not loaded. The range is deliberately narrow — the
    // placeholder has to sit just off the row's Surface, not announce itself.
    val block = lerpColor(PixelPalette.Panel, PixelPalette.BorderDark, level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PixelSpace.Target)
            .background(PixelPalette.Surface)
            .border(PixelSpace.Stroke, PixelPalette.Border)
            .padding(PixelSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PixelSpace.Base),
    ) {
        Box(Modifier.size(SkeletonIconSize).background(block))
        Column(modifier = Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.5f).height(SkeletonNameHeight).background(block))
            Spacer(Modifier.height(PixelSpace.Tight))
            Box(Modifier.fillMaxWidth(0.36f).height(SkeletonMetaHeight).background(block))
        }
        Box(Modifier.size(PixelSpace.Target).background(block))
        Box(Modifier.size(PixelSpace.Target).background(block))
    }
}

/**
 * [count] placeholder rows. The whole list is one accessibility node: a screen reader
 * hears "Loading" once instead of walking a stack of empty boxes that mean nothing on
 * their own.
 */
@Composable
fun PixelSkeletonList(
    modifier: Modifier = Modifier,
    count: Int = SkeletonRows,
) {
    val level = skeletonPulse()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semanticsLoading(),
        verticalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
    ) {
        repeat(count) { PixelSkeletonRow(level) }
    }
}

/** The whole list announces itself once; its blocks say nothing and are not focusable. */
private fun Modifier.semanticsLoading(): Modifier = semantics(mergeDescendants = true) {
    contentDescription = "Loading"
}

/** Rows a loading list draws — enough to fill a phone screen at a glance. */
private const val SkeletonRows = 5

/** Matches [TargetRow]'s icon slot, so a loaded row lands exactly on its placeholder. */
private val SkeletonIconSize = 40.dp

/** One line of the row's title and one of its package id, at roughly those text heights. */
private val SkeletonNameHeight = 12.dp
private val SkeletonMetaHeight = 10.dp

/** Slow enough to read as breathing rather than as a progress bar. */
private const val SkeletonMillis = 700

/** Straight mix in sRGB; the palette's blocks are flat colours, so no colour-space work. */
private fun lerpColor(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = 1f,
)
