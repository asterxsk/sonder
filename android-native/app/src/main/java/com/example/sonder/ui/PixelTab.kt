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
 * padding and differ wildly in how much of their em box they fill: at a shared 26sp,
 * `◎` draws a small ring floating in a large gap while `⚙` fills its box. Each size
 * below is the one that lands at the same optical height as the others, not a size
 * chosen for its own tab.
 */
enum class PixelTab(val glyph: String, val label: String, val glyphSize: TextUnit) {
    HOME("⌂", "Home", 26.sp),
    TARGETS("◎", "Targets", 38.sp),
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
