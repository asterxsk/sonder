package com.example.sonder.ui.kit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft

/**
 * A decision that must be taken on purpose, drawn as one framed panel over the screen's
 * own ground. There is no scrim tint and no blur — the pixel world has neither — so the
 * panel replaces the content rather than dimming it, and the dock stays where it is as
 * the screen's persistent chrome.
 *
 * [confirmStyle] defaults to DANGER because the answer behind the question is usually
 * destructive — a removal, a discard — and that answer should look like what it does. The
 * dismiss control is always the quiet one, so nothing here can be answered "yes" by
 * momentum.
 */
@Composable
fun PixelConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "CANCEL",
    confirmStyle: PixelButtonStyle = PixelButtonStyle.DANGER,
) {
    // The panel owns the whole screen, so Back must answer it rather than reach the nav
    // host: without this, Back dismisses the question *and* the screen behind it, and a
    // user answering a modal is thrown somewhere they never asked to go. Dismiss is the
    // only thing Back is allowed to mean here, so momentum can never land on the confirm.
    BackHandler(onBack = onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .padding(horizontal = PixelSpace.Room),
        contentAlignment = Alignment.Center,
    ) {
        PixelCard(
            modifier = Modifier.fillMaxWidth(),
            variant = PixelCardVariant.SOLID,
            padding = CardPadding.MD,
            // The card is framed in the colour of the answer it is asking for, so a removal
            // is legible as a removal before the buttons are read.
            intent = when (confirmStyle) {
                PixelButtonStyle.DANGER -> PixelPalette.Danger
                PixelButtonStyle.SUCCESS -> PixelPalette.Success
                else -> null
            },
        ) {
            androidx.compose.material3.Text(
                text = title,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.material3.Text(
                text = body,
                style = MonoTypeScale.Body,
                color = TextSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = PixelSpace.Snug),
            )
            PixelCard.Footer {
                PixelButton(
                    text = dismissText,
                    onClick = onDismiss,
                    style = PixelButtonStyle.SECONDARY,
                    modifier = Modifier.weight(1f),
                )
                PixelButton(
                    text = confirmText,
                    onClick = onConfirm,
                    style = confirmStyle,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
