package com.example.sonder.ui.kit

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft

/**
 * design_v3.md §8: target rows are inventory-like objects — pixel border, framed
 * icon box, name + package metadata, amber toggle affordance.
 * The framed box holds the real launcher bitmap when one resolved; [iconGlyph] is
 * the fallback for a null icon, so a row never draws an empty frame. Enabled rows
 * carry the amber border; disabled rows stay visibly dead with the dark border and
 * a muted glyph.
 *
 * The row hosts two sibling controls, not one merged node: the toggle area is a
 * switch node read as "name, Limited / Not limited", and the trailing `→` is its
 * own button that navigates inside Sonder to the app's per-app settings screen. They
 * stay siblings so the arrow's tap is not swallowed by the switch's merge. The state
 * chip is folded into the toggle node — it decorates the switch rather than speaking
 * as loose characters.
 */
@Composable
fun TargetRow(
    appName: String,
    packageName: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconGlyph: String = "▣",
    iconBitmap: Bitmap? = null,
    onOpenSettings: () -> Unit = {},
) {
    // Wrap once per bitmap: re-wrapping on every recomposition would allocate a fresh
    // ImageBitmap for every visible row.
    val iconImage = remember(iconBitmap) { iconBitmap?.asImageBitmap() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PixelSpace.Target)
            .background(PixelPalette.Surface)
            .border(
                PixelSpace.Stroke,
                if (enabled) PixelPalette.Border else PixelPalette.BorderDark,
            )
            .padding(PixelSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Toggle area — the row's switch, and the only node that flips it.
        Row(
            modifier = Modifier
                .weight(1f)
                // §19's target minimum on the interactive node itself: a plain Row measures
                // to its content (the 40dp icon box), it does not stretch to the outer Row's
                // heightIn. fillMaxHeight would be unbounded inside a LazyColumn item.
                .heightIn(min = PixelSpace.Target)
                .toggleable(value = enabled, role = Role.Switch, onValueChange = { onClick() })
                .semantics(mergeDescendants = true) {
                    contentDescription = appName
                    stateDescription = if (enabled) "Limited" else "Not limited"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Framed icon box — stepped corners, amber ink when limited.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(IconBoxSize)
                    .background(PixelPalette.Panel)
                    .border(
                        PixelSpace.Stroke,
                        if (enabled) PixelPalette.Border else PixelPalette.BorderDark,
                    )
                    .pixelSteppedCorners(),
            ) {
                if (iconImage != null) {
                    // Stepped corners are nicked 4dp into the fill, so the bitmap must clear
                    // SteppedCornerInset (and the stroke) or it paints over the niche grammar
                    // on every row that resolves a real icon. PixelPanel insets the same way.
                    Image(
                        bitmap = iconImage,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(SteppedCornerInset + PixelSpace.Stroke),
                    )
                } else {
                    androidx.compose.material3.Text(
                        iconGlyph,
                        color = if (enabled) PixelPalette.Primary else PixelPalette.Muted,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = PixelSpace.Base),
            ) {
                androidx.compose.material3.Text(
                    text = appName,
                    style = PixelTypeScale.SectionTitle,
                    fontFamily = PixelFont,
                    color = PixelPalette.Text,
                )
                androidx.compose.material3.Text(
                    text = packageName,
                    style = MonoTypeScale.PackageId,
                    color = TextSoft,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .background(PixelPalette.Panel)
                    .border(
                        PixelSpace.Stroke,
                        if (enabled) PixelPalette.Primary else PixelPalette.BorderDark,
                    )
                    .padding(horizontal = PixelSpace.Snug, vertical = PixelSpace.Tight),
            ) {
                androidx.compose.material3.Text(
                    text = if (enabled) "ON" else "OFF",
                    style = PixelTypeScale.Badge,
                    fontFamily = PixelFont,
                    color = if (enabled) PixelPalette.Primary else PixelPalette.Muted,
                )
            }
        }
        // §8's "there is more behind this row" affordance, now a real target: a sibling
        // of the toggle, sized to the §19 target minimum so its tap clears 48dp. The glyph
        // stays centered; only the invisible hit box grows past the 40dp icon box.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = PixelSpace.Snug)
                .size(PixelSpace.Target)
                .clickable(role = Role.Button, onClick = onOpenSettings)
                .semantics { contentDescription = "Open $appName settings" },
        ) {
            androidx.compose.material3.Text(
                text = "→",
                style = PixelTypeScale.Badge,
                color = if (enabled) PixelPalette.Primary else PixelPalette.Muted,
            )
        }
    }
}

/** §8's framed icon box. 40dp nominal, as the doc specifies. */
private val IconBoxSize = 40.dp
