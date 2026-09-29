package com.example.sonder.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
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
 * The [content] padding already clears the floating pill (its margins included), the
 * system bars, and the keyboard, so a screen applies it once and never fights a cutout,
 * the gesture area, or a search field's IME. Onboarding and the blackjack gate stay
 * separate full-screen flows without this scaffold.
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
    // safeDrawing is systemBars ∪ ime, and that union takes the larger of the two rather
    // than their sum — so this already carries the keyboard when one is up, and the dock
    // below floats above it for free. Adding the ime inset on top of it would count the
    // keyboard twice and lift the content a whole second keyboard height off the screen.
    val insetBottom = insets.calculateBottomPadding()

    Box(modifier = modifier.fillMaxSize().background(PixelPalette.Bg)) {
        content(
            PaddingValues(
                start = insetStart,
                top = insetTop,
                end = insetEnd,
                // Clear the pill's own margin, the pill, and the gap back up to the content.
                bottom = insetBottom + PixelDockMargin + PixelDockHeight + PixelDockMargin,
            ),
        )
        PixelDock(
            selected = selectedTab,
            onSelect = onSelectTab,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = insetStart + PixelDockMargin,
                    end = insetEnd + PixelDockMargin,
                    bottom = insetBottom + PixelDockMargin,
                ),
        )
    }
}
