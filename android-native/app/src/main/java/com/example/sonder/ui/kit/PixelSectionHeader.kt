package com.example.sonder.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft

/**
 * A screen's title and the one line that says what the screen is for.
 *
 * Every screen already drew the title; what they disagreed about was everything under it.
 * Home pairs its wordmark with a tagline, Targets and the picker put nothing, Stats starts
 * a summary row immediately, and Settings has a permission panel. The result was four
 * different amounts of air under the same size of heading — which reads as four screens
 * that happen not to belong to each other.
 *
 * [subtitle] is optional and [trailing] is for the rare screen whose heading is also where
 * a control lives. The title is a semantic heading so a screen reader's navigation lands on
 * it rather than only on the first control.
 */
@Composable
fun PixelSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            androidx.compose.material3.Text(
                text = title,
                style = PixelTypeScale.ScreenTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Primary,
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                androidx.compose.material3.Text(
                    text = subtitle,
                    style = MonoTypeScale.Metadata,
                    color = TextSoft,
                    // Tighter than the ramp's Snug: a subtitle belongs to its title, and at
                    // 8dp it read as the first line of the body instead of the last line of
                    // the heading.
                    modifier = Modifier.padding(top = PixelSpace.Tight),
                )
            }
        }
        if (trailing != null) {
            Row(
                modifier = Modifier.padding(start = PixelSpace.Base, top = PixelSpace.Tight),
                verticalAlignment = Alignment.CenterVertically,
                content = { trailing() },
            )
        }
    }
}

/**
 * A title for a group inside a screen — LIMITED APPS, RECENT HANDS. Sentence case would be
 * the quieter choice, but the pixel face has no lowercase cut, so these are the one place
 * the app shouts; keeping them a named component stops the shout from also varying in size,
 * colour and gap between screens.
 */
@Composable
fun PixelGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Text(
        text = text,
        style = PixelTypeScale.Badge,
        fontFamily = PixelFont,
        color = TextSoft,
        modifier = modifier.semantics { heading() },
    )
}
