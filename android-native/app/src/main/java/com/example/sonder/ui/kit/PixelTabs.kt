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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelMotion
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft

/**
 * design_v3.md §7: game-menu tabs, not Material segmented controls.
 * Active: amber fill, dark text. Inactive: dark surface, olive border, TextSoft label.
 * Equal-weight segments keep the row filled at any width; each one is a real Tab with
 * a selected state, the shared focus ring, a pressed translate (2px, pixel-style),
 * and a 48dp touch target.
 */
@Composable
fun PixelTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One source per segment, held for the whole row and keyed only by how many segments
    // there are. Calling `remember` inside the loop below left each source keyed on nothing
    // but its position in a loop that re-runs — the row can gain a segment (a value that
    // matches no preset gets its own CUSTOM tab), and a source held by the wrong index
    // would move a focus ring or a press onto the wrong segment.
    val interactions = remember(tabs.size) { List(tabs.size) { MutableInteractionSource() } }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PixelSpace.Stroke),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            val interaction = interactions[index]
            val pressed by interaction.collectIsPressedAsState()
            val focused by interaction.collectIsFocusedAsState()
            // §18 "instant pixel-menu selection": the tab translate is the same 2-frame
            // press as PixelButton, quantised, so the segmented row and the buttons
            // below it move in one motion vocabulary.
            val pressOffset by animateDpAsState(
                targetValue = if (pressed) PixelSpace.Stroke else 0.dp,
                animationSpec = tween(
                    durationMillis = PixelMotion.PressMillis,
                    easing = PixelMotion.Stepped,
                ),
                label = "pixelTabPress",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = PixelSpace.Target)
                    .offset(y = pressOffset)
                    .background(if (active) PixelPalette.Primary else PixelPalette.Surface)
                    .border(
                        PixelSpace.Stroke,
                        if (active) PixelPalette.Primary else PixelPalette.BorderDark,
                    )
                    .pixelFocusRing(visible = focused)
                    .selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = interaction,
                        indication = null,
                    ) { onSelect(index) }
                    .padding(horizontal = PixelSpace.Snug, vertical = PixelSpace.Base),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = label,
                    style = PixelTypeScale.Button,
                    fontFamily = PixelFont,
                    color = if (active) PixelPalette.Bg else TextSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
