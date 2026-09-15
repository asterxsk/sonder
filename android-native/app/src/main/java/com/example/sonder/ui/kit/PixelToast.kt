package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelPalette

/** Toast tones per design_v3 §16: success / error / info RPG dialogue boxes. */
sealed class ToastTone(val color: Color) {
    data object Success : ToastTone(PixelPalette.Success)
    data object Error : ToastTone(PixelPalette.Danger)
    data object Info : ToastTone(PixelPalette.Info)
}

/**
 * design_v3.md §16: toasts are small RPG dialogue/status boxes — hard borders,
 * pixel shadow, mono body text. E.g. "✓ Access granted. You have 5 minutes."
 */
@Composable
fun PixelToast(
    message: String,
    modifier: Modifier = Modifier,
    tone: ToastTone = ToastTone.Info,
) {
    Box(
        modifier = modifier
            .background(PixelPalette.Panel)
            .border(2.dp, tone.color)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        androidx.compose.material3.Text(
            text = message,
            style = MonoTypeScale.Body,
            color = PixelPalette.Text,
        )
    }
}
