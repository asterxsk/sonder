package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.TextSoft

/**
 * The pixel search field (design_v3 §6 vocabulary, not a Material outline): hard
 * frame, panel ground, mono text, amber caret, the shared focus ring, 48dp target.
 * Type filters the Targets inventory; the IME action is Search.
 */
@Composable
fun PixelSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "SEARCH APPS…",
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MonoTypeScale.Body.copy(color = PixelPalette.Text),
        cursorBrush = SolidColor(PixelPalette.Primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions.Default,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PixelSpace.Target),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PixelPalette.Panel)
                    .border(
                        PixelSpace.Stroke,
                        if (focused) PixelPalette.Primary else PixelPalette.BorderDark,
                    )
                    .pixelFocusRing(visible = focused)
                    .padding(horizontal = PixelSpace.Base, vertical = PixelSpace.Base),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // inner() is always composed — it IS the editable field; the
                // placeholder only overlays it while there is no text.
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        androidx.compose.material3.Text(
                            text = placeholder,
                            style = MonoTypeScale.Body,
                            color = TextSoft,
                        )
                    }
                    inner()
                }
            }
        },
    )
}
