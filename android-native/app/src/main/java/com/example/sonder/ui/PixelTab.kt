package com.example.sonder.ui

import androidx.navigation3.runtime.NavKey
import com.example.sonder.Main
import com.example.sonder.Settings
import com.example.sonder.Stats
import com.example.sonder.Targets

/**
 * The four dock destinations, in dock order. HOME is the root.
 * Glyphs and labels are the fixed design_v3 §15 dock text; they live here so no
 * composable rebuilds them.
 */
enum class PixelTab(val glyph: String, val label: String) {
    HOME("⌂", "HOME"),
    TARGETS("◎", "TARGETS"),
    STATS("▥", "STATS"),
    SETTINGS("⚙", "SETTINGS");

    /** The Nav3 destination this tab shows. */
    val key: NavKey
        get() = when (this) {
            HOME -> Main
            TARGETS -> Targets
            STATS -> Stats
            SETTINGS -> Settings
        }
}
