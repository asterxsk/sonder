package com.example.sonder.ui

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import com.example.sonder.Main
import com.example.sonder.Settings
import com.example.sonder.Stats
import com.example.sonder.Targets

/**
 * The four dock destinations, in dock order. HOME is the root.
 * Glyphs and labels are the fixed design_v3 §15 dock text; they live here so no
 * composable rebuilds them. Labels are sentence case and double as the tabs'
 * accessibility names, which read better as words than as shouted capitals.
 *
 * [glyphSize] is per tab because none of these are drawn in the app's own fonts. They
 * come from the platform symbol font, whose characters carry their own built-in
 * padding and differ wildly in how much of their em box they fill.
 *
 * The ceiling on that size is the label below it, not taste: §15 asks for both a glyph
 * and a label inside a 64dp pill with 8dp of padding, which leaves a 48dp column. The
 * glyph is measured first and the label is measured with whatever height is left, so a
 * glyph whose line box fills that column does not overlap the label — it leaves the
 * label zero height and the tab renders as "just a logo". §15's 38sp for `◎` was
 * measured for optical parity on its own and cannot fit above a label in this dock;
 * 26sp is the largest size the dock can hold, and it is what the other three use.
 *
 * TARGETS is `⌖` (U+2316, position indicator), not the `◎` §15 originally specified.
 * `◎` renders a 30px ring at 26sp against `⌂`'s 43px, so Targets read as the shrunk tab,
 * and no per-tab size can close that: §15's ceiling applies to all four, and the whole
 * Geometric Shapes block is drawn small — `◉` and `◍`, the obvious alternatives, measure
 * 31px at the same size. `⌖` is from the Miscellaneous Technical block beside `⌂` and its
 * ink width matches Home's exactly; §15 records the measurements.
 */
enum class PixelTab(val glyph: String, val label: String, val glyphSize: TextUnit) {
    HOME("⌂", "Home", 26.sp),
    TARGETS("⌖", "Targets", 26.sp),
    STATS("▥", "Stats", 26.sp),
    SETTINGS("⚙", "Settings", 26.sp);

    /** The Nav3 destination this tab shows. */
    val key: NavKey
        get() = when (this) {
            HOME -> Main
            TARGETS -> Targets
            STATS -> Stats
            SETTINGS -> Settings
        }
}
