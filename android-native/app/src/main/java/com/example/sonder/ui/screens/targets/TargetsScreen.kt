package com.example.sonder.ui.screens.targets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelLoader
import com.example.sonder.ui.kit.PixelEmptyState
import com.example.sonder.ui.kit.PixelSectionHeader
import com.example.sonder.ui.kit.PixelSkeletonList
import com.example.sonder.ui.kit.TargetRow

/**
 * Targets: the apps gated behind blackjack (§8). It opens on the added set only — no
 * tabs, no search — and ADD is the single way in. The dock, background, and system
 * insets come from the shared [PaddingValues]; there is no Back or DONE, so the list
 * keeps the screen height and the dock is the way out.
 *
 * A row has one control: the arrow that opens the app's settings screen. Both the rule and
 * the removal live there, behind the holds that make changing either a deliberate act, so
 * this list is a list — nothing on it can change what is being enforced.
 */
@Composable
fun TargetsScreen(
    contentPadding: PaddingValues,
    onOpenAppSettings: (String) -> Unit,
    onAddApps: () -> Unit,
    viewModel: TargetsViewModel = hiltViewModel(),
) {
    val view by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = PixelSpace.Room),
    ) {
        // A subtitle rather than a bare title: this screen is the only place that says
        // what the list is *for*, and an empty list on a bare heading reads as a list
        // that failed to load.
        Spacer(Modifier.height(PixelSpace.Room))
        PixelSectionHeader(
            title = "TARGETS",
            subtitle = "Apps that ask you to win a hand first",
        )
        Spacer(Modifier.height(PixelSpace.Room))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val state = view) {
                // The waiting state is the list's own shape rather than a panel in
                // the middle of an empty screen: nothing moves when the rows arrive.
                TargetsViewState.Loading -> PixelSkeletonList(
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                TargetsViewState.NoLaunchableApps -> FramedNote(
                    title = "NO LAUNCHABLE APPS",
                    body = "This device reports nothing with a launcher to gate.",
                )
                TargetsViewState.LoadFailed -> FramedNote(
                    title = "APPS UNAVAILABLE",
                    body = "The system did not return the app list. This is not an empty device.",
                    tone = PixelPalette.Danger,
                ) {
                    PixelButton(
                        text = "TRY AGAIN",
                        onClick = viewModel::retry,
                        style = PixelButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TargetsViewState.Empty -> FramedNote(
                    title = "NO APPS LIMITED",
                    body = "Add an app to gate it behind a hand of blackjack.",
                ) {
                    AddAppsButton(onClick = onAddApps, modifier = Modifier.fillMaxWidth())
                }
                is TargetsViewState.Rows -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
                ) {
                    items(state.picks, key = { it.packageName }) { pick ->
                        TargetRow(
                            appName = pick.label,
                            packageName = pick.packageName,
                            onOpen = onOpenAppSettings,
                            iconBitmap = pick.icon,
                            scopeNote = blockScopeNote(pick.packageName, pick.blockScope),
                        )
                    }
                }
            }
        }

        // Outside the scrolling list, above the dock inset, so it never drifts over the
        // rows or hides the last one. Only shown when there are rows — the empty state
        // carries the same control as its action instead.
        if (view is TargetsViewState.Rows) {
            Spacer(Modifier.height(PixelSpace.Base))
            AddAppsButton(onClick = onAddApps, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The one ADD control, so the empty state's action and the pinned bottom control are
 * the same composable rather than two buttons that could drift apart.
 */
@Composable
private fun AddAppsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PixelButton(
        text = "ADD",
        onClick = onClick,
        style = PixelButtonStyle.PRIMARY,
        modifier = modifier,
    )
}

/**
 * Framed explanatory state — a card, not a blank list, so empty never reads as frozen.
 * [action] is the optional control a state needs; the others pass none.
 *
 * Kept as an alias rather than deleted: Targets and the picker both call it, and both are
 * describing the same three states, so one shared shape is the point.
 */
@Composable
internal fun FramedNote(
    title: String,
    body: String,
    busy: Boolean = false,
    tone: Color = PixelPalette.Muted,
    action: (@Composable () -> Unit)? = null,
) {
    PixelEmptyState(
        title = title,
        body = body,
        busy = busy,
        tone = tone,
        // The states that describe a fault carry the app's own mark for it; the two that
        // describe an ordinary empty list carry nothing, because an empty list is not an
        // error and dressing it as one is the habit this replaces.
        glyph = when (tone) {
            PixelPalette.Danger -> "▣"
            else -> null
        },
        action = action,
    )
}
