package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.PixelTab

/** Dock bar height. Shared with [PixelAppScaffold] so content padding clears the bar. */
internal val PixelDockHeight = 64.dp

/**
 * design_v3.md §15: bottom navigation is a console dock — equal-width tabs in a
 * hard-framed bar, active tab amber-on-panel with a 2px top marker. Pressed = 2px
 * translate, pixel-style. 64dp tall so the system gesture area never eats taps.
 * The fixed tab order, glyphs, and labels come from [PixelTab], not from a list
 * rebuilt on every recomposition.
 */
@Composable
fun PixelDock(
    selected: PixelTab,
    onSelect: (PixelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PixelDockHeight)
            .background(PixelPalette.Surface)
            .border(2.dp, PixelPalette.Border),
    ) {
        PixelTab.entries.forEachIndexed { index, tab ->
            val active = tab == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .offset(y = if (pressed) 2.dp else 0.dp)
                    .background(if (active) PixelPalette.Panel else PixelPalette.Surface)
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = interaction,
                        indication = null,
                    ) { onSelect(tab) }
                    // One merged node per tab: the label is the readable name, so the
                    // glyph is never spelled out as a stray character, and `selectable`
                    // supplies the selected state.
                    .semantics(mergeDescendants = true) { contentDescription = tab.label },
                contentAlignment = Alignment.Center,
            ) {
                // Active marker: amber bar on the top edge.
                if (active) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(PixelPalette.Primary)
                            .align(Alignment.TopCenter),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    androidx.compose.material3.Text(
                        tab.glyph,
                        color = if (active) PixelPalette.Primary else PixelPalette.Muted,
                    )
                    androidx.compose.material3.Text(
                        tab.label,
                        style = PixelTypeScale.Nav,
                        fontFamily = PixelFont,
                        color = if (active) PixelPalette.Primary else PixelPalette.Muted,
                    )
                }
            }

            // Divider between tabs (not after the last).
            if (index < PixelTab.entries.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(2.dp)
                        .background(PixelPalette.BorderDark),
                )
            }
        }
    }
}
