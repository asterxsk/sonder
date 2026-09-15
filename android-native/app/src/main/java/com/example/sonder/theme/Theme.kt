package com.example.sonder.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val PixelColorScheme = darkColorScheme(
    primary = PixelPalette.Primary,
    onPrimary = PixelPalette.Bg,
    primaryContainer = PixelPalette.Primary,
    onPrimaryContainer = PixelPalette.Bg,
    secondary = PixelPalette.Border,
    onSecondary = PixelPalette.Bg,
    background = PixelPalette.Bg,
    onBackground = PixelPalette.Text,
    surface = PixelPalette.Surface,
    onSurface = PixelPalette.Text,
    surfaceVariant = PixelPalette.Panel,
    onSurfaceVariant = PixelPalette.Muted,
    outline = PixelPalette.Border,
    error = PixelPalette.Danger,
    onError = PixelPalette.Bg,
)

/** Pixel UI has zero radius everywhere (design_v3 §5: no soft elevation, no blur). */
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun SonderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PixelColorScheme,
        typography = PixelTypography,
        shapes = PixelShapes,
        content = content,
    )
}
