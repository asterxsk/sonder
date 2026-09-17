package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale

/**
 * design_v3.md §7: game-menu tabs, not Material segmented controls.
 * Active: amber fill, dark text. Inactive: dark surface, olive border, muted text.
 * Equal-weight segments keep the row filled at any width; each one is a Tab with a
 * selected state and a 48dp touch target.
 */
@Composable
fun PixelTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .background(if (active) PixelPalette.Primary else PixelPalette.Surface)
                    .border(2.dp, if (active) PixelPalette.Primary else PixelPalette.BorderDark)
                    .selectable(selected = active, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Text(
                    text = label,
                    style = PixelTypeScale.Button,
                    fontFamily = PixelFont,
                    color = if (active) PixelPalette.Bg else PixelPalette.Muted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
