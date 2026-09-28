package com.example.sonder.ui.kit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.PixelTab

/** Dock bar height. Shared with [PixelAppScaffold] so content padding clears the bar. */
internal val PixelDockHeight = 64.dp

/**
 * design_v3.md §15: bottom navigation is a console dock — equal-width tabs in a
 * hard-framed bar. The active tab carries the amber frame: top marker, bottom
 * marker, amber glyph, raised panel ground. Pressed = 2px translate, pixel-style.
 * Inactive items sit at TextSoft, so labels stay readable (≥7:1) without competing
 * with the active amber. Every tab is a real selected Tab role with the shared
 * keyboard/switch-access focus ring; 64dp tall so the gesture area never eats taps.
 * The fixed tab order, glyphs, and labels come from [PixelTab], not a rebuilt list.
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
            .border(PixelSpace.Stroke, PixelPalette.Border),
    ) {
        PixelTab.entries.forEachIndexed { index, tab ->
            val active = tab == selected
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            // §18: same 2-frame quantised press as PixelButton and PixelTabs.
            val pressOffset by animateDpAsState(
                targetValue = if (pressed) PixelSpace.Stroke else 0.dp,
                animationSpec = tween(
                    durationMillis = PixelMotion.PressMillis,
                    easing = PixelMotion.Stepped,
                ),
                label = "pixelDockPress",
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .offset(y = pressOffset)
                    .background(if (active) PixelPalette.Panel else PixelPalette.Surface)
                    .pixelFocusRing(visible = focused)
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
                if (active) {
                    // §15 active markers: amber bars top and bottom inside the frame. The
                    // top bar is a pixel heavier than the bottom one, which is what makes
                    // the tab read as lit rather than as evenly underlined.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(PixelPalette.Primary)
                            .align(Alignment.TopCenter),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(PixelSpace.Stroke)
                            .background(PixelPalette.Primary)
                            .align(Alignment.BottomCenter),
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    androidx.compose.material3.Text(
                        tab.glyph,
                        color = if (active) PixelPalette.Primary else TextSoft,
                    )
                    androidx.compose.material3.Text(
                        tab.label,
                        style = PixelTypeScale.Nav,
                        fontFamily = PixelFont,
                        color = if (active) PixelPalette.Primary else TextSoft,
                    )
                }
            }

            // Divider between tabs (not after the last).
            if (index < PixelTab.entries.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(PixelSpace.Stroke)
                        .background(PixelPalette.BorderDark),
                )
            }
        }
    }
}
