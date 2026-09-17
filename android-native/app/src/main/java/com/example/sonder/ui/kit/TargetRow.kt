package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale

/**
 * design_v3.md §8: target rows are inventory-like objects — pixel border, 40dp framed
 * icon box, name + package metadata, amber ON/OFF toggle affordance.
 * The whole row is the toggle: one switch node read as "name, Limited / Not limited",
 * so the action makes sense without decoding the package ID. The glyph is a string,
 * never a decoded launcher bitmap.
 */
@Composable
fun TargetRow(
    appName: String,
    packageName: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconGlyph: String = "▣",
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(PixelPalette.Surface)
            .border(2.dp, PixelPalette.BorderDark)
            .toggleable(value = enabled, role = Role.Switch, onValueChange = { onClick() })
            .semantics(mergeDescendants = true) {
                contentDescription = appName
                stateDescription = if (enabled) "Limited" else "Not limited"
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Framed 40dp icon box
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .border(2.dp, PixelPalette.Border, RectangleShape),
        ) {
            androidx.compose.material3.Text(iconGlyph, color = PixelPalette.Text)
        }
        Column(modifier = Modifier
            .weight(1f)
            .padding(horizontal = 10.dp)) {
            androidx.compose.material3.Text(
                text = appName,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
            )
            androidx.compose.material3.Text(
                text = packageName,
                style = MonoTypeScale.PackageId,
                color = PixelPalette.Muted,
            )
        }
        androidx.compose.material3.Text(
            text = if (enabled) "ON" else "OFF",
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = if (enabled) PixelPalette.Primary else PixelPalette.Muted,
        )
    }
}
