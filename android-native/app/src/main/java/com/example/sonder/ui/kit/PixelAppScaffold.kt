package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import com.example.sonder.theme.PixelPalette
import com.example.sonder.ui.PixelTab

/**
 * design_v3.md §Management UI: the shared management shell. It owns the safe-drawing
 * insets, the near-black background, and the single persistent dock — screens supply
 * only their own content and current tab.
 *
 * The [content] padding already clears the dock and the system bars, so a screen
 * applies it once and never fights a cutout or the gesture area. Onboarding and the
 * blackjack gate stay separate full-screen flows without this scaffold.
 */
@Composable
fun PixelAppScaffold(
    selectedTab: PixelTab,
    onSelectTab: (PixelTab) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (PaddingValues) -> Unit,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val insetStart = insets.calculateStartPadding(layoutDirection)
    val insetEnd = insets.calculateEndPadding(layoutDirection)
    val insetTop = insets.calculateTopPadding()
    val insetBottom = insets.calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize().background(PixelPalette.Bg)) {
        content(
            PaddingValues(
                start = insetStart,
                top = insetTop,
                end = insetEnd,
                bottom = insetBottom + PixelDockHeight,
            ),
        )
        PixelDock(
            selected = selectedTab,
            onSelect = onSelectTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = insetStart, end = insetEnd, bottom = insetBottom),
        )
    }
}
