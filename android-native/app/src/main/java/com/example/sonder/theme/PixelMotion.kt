package com.example.sonder.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import kotlin.math.floor

/**
 * design_v3.md §18: discrete steps rather than smooth interpolation — a 2–4 frame
 * button press, a 2-frame card flip, instant pixel-menu selection — and explicitly no
 * ease-in-out, no spring physics, no smooth floating.
 *
 * That is a real constraint on Compose, whose defaults are all built from continuous
 * easing curves. [Stepped] is how the world is kept: it quantises any interpolation to
 * [Steps] discrete frames, so a colour or alpha change lands on-frame instead of
 * sliding. Nothing here is decorative — it exists so a control never eases.
 */
object PixelMotion {
    /** 2 frames at 60fps: the press translate, and the dock's pressed offset. */
    const val PressMillis = 90

    /** Near-instant: menu and tab selection. Selection must feel like a key press. */
    const val SelectMillis = 60

    /** 3 frames: a live state change (badge, timer tone, toast in and out). */
    const val StateMillis = 180

    /** 3–5 frames: the win sparkle, the one authored moment on the block screen. */
    const val WinMillis = 420

    /**
     * One card landing on the table: the table's deal-in, four stepped frames at
     * [Steps]. The travel is the card's own 16dp drop, so the block reads as a card
     * being put down rather than as one being switched on.
     */
    const val DealMillis = 200

    /**
     * Between two cards of the same deal — 90ms is five frames at 60fps, so the cards
     * of a hand read as dealt in sequence and never as two arriving at once.
     */
    const val DealStaggerMillis = 90L

    /**
     * §18's card flip. The doc's "2 frames" is the pixel-art trope; at 60fps those two
     * frames are 33ms, which is a card that has already turned over by the time the eye
     * catches it. Four frames at [Steps] is the same hard-edged turn at a length that
     * can actually be read as a turn.
     */
    const val FlipMillis = 240

    /** Frames every interpolation is quantised to. v3's ceiling is 4–5. */
    const val Steps = 4

    /**
     * Quantises easing to [Steps] discrete frames. At `Steps = 4` a 180ms fade draws four
     * hard steps and no intermediate values, which is what reads as pixel motion rather
     * than as a slow fade. Use in place of the Material defaults on any tween this world
     * runs: `tween(durationMillis = StateMillis, easing = PixelMotion.Stepped)`.
     */
    val Stepped: Easing = Easing { fraction -> floor(fraction * Steps) / Steps }

    /**
     * Reserved for progress that is genuinely linear — the loader's fill cycle, where a
     * stepped easing would fight the loop's own rhythm. Everything else uses [Stepped].
     */
    val Continuous: Easing = LinearEasing
}
