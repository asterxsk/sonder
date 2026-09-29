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
import androidx.compose.foundation.layout.widthIn
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
 * One row action counting down, already resolved into what the row draws. The row is
 * told what to show, not which delayed action it belongs to — the hold lengths and the
 * countdown itself are the Targets screen's business, and the kit stays below it.
 * [onClick] is the cancel: the strip is the control that started the wait, so tapping
 * it again is how the user backs out.
 */
data class TargetRowPending(
    val label: String,
    val description: String,
    val tone: Color,
    val onClick: () -> Unit,
)

/**
 * design_v3.md §8: target rows are inventory-like objects — pixel border, framed icon
 * box, name + package metadata. The row is no longer a single switch: a target is
 * added and removed deliberately, not toggled in place, so the whole row is inert and
 * its only controls are the two framed glyphs that start the delayed edit and remove.
 * The framed box holds the real launcher bitmap when one resolved; [iconGlyph] is the
 * fallback for a null icon, so a row never draws an empty frame.
 *
 * [pending] is non-null only for the row whose action is counting down; that row swaps
 * both glyphs for one [PixelDelayedButton], so the wait is visible rather than a
 * control that appears to have done nothing.
 */
@Composable
fun TargetRow(
    appName: String,
    packageName: String,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    pending: TargetRowPending? = null,
    iconGlyph: String = "▣",
    iconBitmap: Bitmap? = null,
) {
    // Wrap once per bitmap: re-wrapping on every recomposition would allocate a fresh
    // ImageBitmap for every visible row.
    val iconImage = remember(iconBitmap) { iconBitmap?.asImageBitmap() }
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
        }
        if (pending != null) {
            PixelDelayedButton(
                text = pending.label,
                contentDescription = pending.description,
                onClick = pending.onClick,
                tone = pending.tone,
                modifier = Modifier.widthIn(min = DelayedStripMinWidth),
            )
        } else {
            RowGlyphButton(
                glyph = "✎",
                description = "Edit $appName settings",
                tone = PixelPalette.Primary,
                onClick = onEdit,
            )
            RowGlyphButton(
                glyph = "✕",
                description = "Remove $appName",
                tone = PixelPalette.Danger,
                onClick = onRemove,
                modifier = Modifier.padding(start = PixelSpace.Snug),
            )
        }
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

/** Enough for a `REMOVE 0:24` readout, so the strip is legible, not a squeezed chip. */
private val DelayedStripMinWidth = 112.dp
