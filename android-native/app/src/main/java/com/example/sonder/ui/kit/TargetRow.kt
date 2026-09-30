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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft

/**
 * design_v3.md §8: target rows are inventory-like objects — pixel border, framed icon
 * box, name + package metadata. The row is not a switch: a target is added and removed
 * deliberately, not toggled in place, so the row is inert and its one control is the arrow
 * that opens the app's own settings screen, where both the rule and the removal live. The
 * framed box holds the real launcher bitmap when one resolved; [iconGlyph] is the fallback
 * for a null icon, so a row never draws an empty frame.
 *
 * [scopeNote] is set only for a target that does not gate the whole app, and it is not
 * decoration: without it "Instagram is limited" reads as the whole app being blocked, which
 * is the opposite of what a Reels-only target does, and the user would have no way to tell
 * the two apart from this list. Null means whole-app and draws nothing, which is the
 * overwhelming majority of rows.
 */
@Composable
fun TargetRow(
    appName: String,
    packageName: String,
    // The callback takes the package rather than closing over it, so the list can hand
    // one stable lambda to every row instead of allocating one per row per
    // recomposition — an allocation a LazyColumn can never hoist for itself.
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
    iconGlyph: String = "▣",
    iconBitmap: Bitmap? = null,
    scopeNote: String? = null,
) {
    // Wrap once per bitmap: re-wrapping on every recomposition would allocate a fresh
    // ImageBitmap for every visible row.
    val iconImage = remember(iconBitmap) { iconBitmap?.asImageBitmap() }

    // Bound once per package, so the row's own button holds a stable reference even though
    // the callback above is shared by every row.
    val open = remember(packageName, onOpen) { { onOpen(packageName) } }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PixelSpace.Target)
            .background(PixelPalette.Surface)
            .border(PixelSpace.Stroke, PixelPalette.Border)
            .padding(PixelSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The launcher icon sits bare on the row's own ground. A frame around it was a
        // second border competing with the row's: the row already reads as one framed
        // object, so boxing the icon made a picture-in-a-box instead of a list row.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(IconBoxSize),
        ) {
            if (iconImage != null) {
                Image(
                    bitmap = iconImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Only an app that resolved no bitmap falls back to a glyph; amber keeps
                // it legible where a real icon would have carried its own colour.
                androidx.compose.material3.Text(
                    iconGlyph,
                    color = PixelPalette.Primary,
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
            if (scopeNote != null) {
                androidx.compose.material3.Text(
                    text = scopeNote,
                    style = MonoTypeScale.Metadata,
                    color = PixelPalette.Primary,
                )
            }
        }
        RowGlyphButton(
            glyph = "›",
            // The arrow opens the app's rule, which is also where its removal lives, so
            // the description names the destination rather than the glyph.
            description = "Open $appName settings",
            tone = PixelPalette.Primary,
            onClick = open,
        )
    }
}

/**
 * One row control: a framed glyph sized to §19's 48dp target, its own node so each
 * action's tap is distinct, and its own [description] so TalkBack names the action
 * rather than the character.
 */
@Composable
private fun RowGlyphButton(
    glyph: String,
    description: String,
    tone: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(PixelSpace.Target)
            .background(PixelPalette.Panel)
            .border(PixelSpace.Stroke, tone)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        androidx.compose.material3.Text(
            text = glyph,
            style = PixelTypeScale.RowGlyph,
            color = tone,
        )
    }
}

/** §8's icon slot. 40dp nominal; unboxed, since the row is the frame. */
private val IconBoxSize = 40.dp
