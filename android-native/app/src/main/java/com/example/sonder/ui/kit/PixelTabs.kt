package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale

/**
 * design_v3.md §7: game-menu tabs, not Material segmented controls.
 * Active: amber fill, dark text. Inactive: dark surface, olive border, muted text.
 */
@Composable
fun PixelTabs(
    tabs: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        tabs.forEachIndexed { index, label ->
            val active = index == selected
            androidx.compose.material3.Text(
                text = label,
                style = PixelTypeScale.Button,
                color = if (active) PixelPalette.Bg else PixelPalette.Muted,
                modifier = Modifier
                    .background(if (active) PixelPalette.Primary else PixelPalette.Surface)
                    .border(2.dp, if (active) PixelPalette.Primary else PixelPalette.BorderDark)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
