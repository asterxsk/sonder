package com.example.sonder.ui.kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft

/**
 * The one empty/error state, in HeroUI's shape: a mark, a title, a sentence explaining the
 * situation, and at most one action that changes it.
 *
 * The four screens that need this each grew their own copy — Targets and the picker shared
 * one, Stats had a second, the permission prompt had a third — and they had drifted: two
 * of them separated the title from the body by 6dp, the third by 8dp, and only one of them
 * could carry an action. This is the union, with the differences made into parameters.
 *
 * [tone] is not decoration. An empty list and a failed load look identical if both are
 * drawn in the quiet neutral, and the difference between "you have not added anything" and
 * "the system did not answer" is the single most useful thing this state can say. Failure
 * takes the danger tone and carries a retry; emptiness takes the neutral one and usually
 * carries nothing.
 */
@Composable
fun PixelEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    glyph: String? = null,
    tone: Color = PixelPalette.Muted,
    busy: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    PixelCard(
        modifier = modifier,
        variant = PixelCardVariant.OUTLINED,
        padding = CardPadding.LG,
        intent = if (tone == PixelPalette.Muted) null else tone,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
        ) {
            // A waiting state draws the app's own loader rather than a glyph: an empty state
            // that is actually still loading must move, or it reads as a screen that has
            // given up.
            when {
                busy -> PixelLoader()
                glyph != null -> androidx.compose.material3.Text(
                    text = glyph,
                    style = PixelTypeScale.Timer,
                    fontFamily = PixelFont,
                    color = tone,
                )
            }
            androidx.compose.material3.Text(
                text = title,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
                textAlign = TextAlign.Center,
            )
            androidx.compose.material3.Text(
                text = body,
                style = MonoTypeScale.Body,
                color = TextSoft,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                // The action sits a step further from the sentence than the sentence does
                // from the title, so the block reads as one statement with one answer rather
                // than as three evenly-spaced lines.
                Column(Modifier.padding(top = PixelSpace.Snug), content = { action() })
            }
        }
    }
}
