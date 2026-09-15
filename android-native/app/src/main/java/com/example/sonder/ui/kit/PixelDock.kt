package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale

/**
 * design_v3.md §15: bottom navigation is a tiny console dock — no floating nav,
 * active tab framed in amber with a top/bottom marker.
 */
@Composable
fun PixelDock(
    tabs: List<Pair<String, String>>, // glyph to label
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PixelPalette.Surface)
            .border(2.dp, PixelPalette.Border)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        tabs.forEachIndexed { index, (glyph, label) ->
            val active = index == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(if (active) PixelPalette.Panel else PixelPalette.Surface)
                    .border(2.dp, if (active) PixelPalette.Primary else PixelPalette.Surface)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                androidx.compose.material3.Text(glyph, color = if (active) PixelPalette.Primary else PixelPalette.Muted)
                androidx.compose.material3.Text(
                    label,
                    style = PixelTypeScale.Nav,
                    fontFamily = PixelFont,
                    color = if (active) PixelPalette.Primary else PixelPalette.Muted,
                )
            }
        }
    }
}
