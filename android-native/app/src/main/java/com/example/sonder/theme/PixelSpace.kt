package com.example.sonder.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * design_v3.md §19 as a ramp. The doc fixes three numbers — 48dp touch targets, 8dp
 * between related elements, 12dp between unrelated ones, on a 16dp gutter — and leaves
 * every other gap to the implementation, which is how the v3 screens ended up with
 * 6/10/14dp one-offs that read as four slightly different designs.
 *
 * The ramp is 4dp-based so every value is a whole number of pixels at mdpi, which is
 * what keeps the hard frames and stepped corners landing on the grid. Prefer a named
 * step over a literal; a gap that needs a value not on this ramp is a signal the
 * layout is doing something the ramp should learn, not a reason to write `13.dp`.
 */
object PixelSpace {
    /** 2dp — frame strokes and dividers. Not a content gap. */
    val Stroke = 2.dp

    /** 4dp — the stepped-corner inset and shadow strip; glued pairs like icon+label. */
    val Tight = 4.dp

    /** 8dp — §19 minimum between related elements (label above its value). */
    val Snug = 8.dp

    /** 12dp — §19 minimum between unrelated elements; the default inside a panel. */
    val Base = 12.dp

    /** 16dp — §19 screen gutter, and the padding of a panel that carries a screen. */
    val Room = 16.dp

    /** 24dp — between sections of one screen. */
    val Section = 24.dp

    /** 32dp — above a screen title and below the last block; breathing room at an edge. */
    val Edge = 32.dp

    /** 48dp — §19 minimum touch target. Every interactive control clears this. */
    val Target = 48.dp
}
