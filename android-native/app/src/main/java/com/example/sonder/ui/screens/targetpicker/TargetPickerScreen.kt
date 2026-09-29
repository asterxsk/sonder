package com.example.sonder.ui.screens.targetpicker

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
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
import com.example.sonder.ui.kit.PixelSearchField
import com.example.sonder.ui.kit.PixelSkeletonList
import com.example.sonder.ui.kit.SteppedCornerInset
import com.example.sonder.ui.kit.pixelSteppedCorners
import com.example.sonder.ui.screens.targets.FramedNote
import com.example.sonder.ui.screens.targets.TargetPickerViewState

/**
 * The add-apps picker, reached from Targets' ADD control. It is ADD-ONLY on purpose:
 * already-added apps are shown checked and inert, with copy pointing at Targets for
 * removal. A picker that could uncheck would be an instant unblock that routes around
 * the 30 s `✕` wait — the exact pause the 30 s exists to enforce. DONE commits the
 * newly ticked packages and pops; CANCEL and Back commit nothing.
 */
@Composable
fun TargetPickerScreen(
    contentPadding: PaddingValues,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: TargetPickerViewModel = hiltViewModel(),
) {
    val view by viewModel.ui.collectAsStateWithLifecycle()
    val query by viewModel.queryText.collectAsStateWithLifecycle()
    val selected by viewModel.selection.collectAsStateWithLifecycle()

    // One shot per *visit*, which is not the same as one per composition: the ViewModel
    // outlives this screen and a rotation rebuilds the composition without the user
    // having gone anywhere, so a bare LaunchedEffect(Unit) would throw away ticks and
    // search text mid-task. rememberSaveable is restored from the nav entry's own saved
    // state across a config change and is fresh for a newly pushed entry, so it is
    // exactly the "have I already started this visit" flag this needs.
    var started by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!started) {
            viewModel.reset()
            started = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = PixelSpace.Room),
    ) {
        Spacer(Modifier.height(PixelSpace.Room))
        androidx.compose.material3.Text(
            "ADD APPS",
            style = PixelTypeScale.ScreenTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(PixelSpace.Room))

        PixelSearchField(
            value = query,
            onValueChange = viewModel::setQuery,
        )
        Spacer(Modifier.height(PixelSpace.Base))

        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (val state = view) {
                // Same as the Targets list: the wait is drawn as the rows it is waiting
                // for, so the catalogue does not appear in a single jump.
                TargetPickerViewState.Loading -> PixelSkeletonList(
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                TargetPickerViewState.NoLaunchableApps -> FramedNote(
                    title = "NO LAUNCHABLE APPS",
                    body = "This device reports nothing with a launcher to gate.",
                )
                TargetPickerViewState.LoadFailed -> FramedNote(
                    title = "APPS UNAVAILABLE",
                    body = "The system did not return the app list. This is not an empty device.",
                ) {
                    PixelButton(
                        text = "TRY AGAIN",
                        onClick = viewModel::retry,
                        style = PixelButtonStyle.SECONDARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                TargetPickerViewState.NoResults -> FramedNote(
                    title = "NO MATCHES",
                    body = "No app on this device matches that search.",
                )
                is TargetPickerViewState.Rows -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(PixelSpace.Snug),
                ) {
                    items(state.picks, key = { it.packageName }) { pick ->
                        PickerRow(
                            appName = pick.label,
                            packageName = pick.packageName,
                            added = pick.enabled,
                            selected = pick.packageName in selected,
                            onToggle = { viewModel.toggleSelection(pick.packageName) },
                            iconBitmap = pick.icon,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(PixelSpace.Base))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PixelSpace.Base),
        ) {
            PixelButton(
                text = "CANCEL",
                onClick = onCancel,
                style = PixelButtonStyle.SECONDARY,
                modifier = Modifier.weight(1f),
            )
            PixelButton(
                text = "DONE",
                onClick = { viewModel.commit(onCommitted = onDone) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(PixelSpace.Base))
    }
}

/**
 * One picker row: icon, label, package, and a framed check. [added] rows are already
 * targets — checked and inert, with a line saying removal lives on Targets — while the
 * rest toggle [selected]. The whole row is the 48dp control and the checkbox is its
 * decoration, so the check node is folded into the row rather than speaking separately.
 */
@Composable
private fun PickerRow(
    appName: String,
    packageName: String,
    added: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
    iconBitmap: Bitmap? = null,
) {
    val checked = added || selected
    val iconImage = remember(iconBitmap) { iconBitmap?.asImageBitmap() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PixelSpace.Target)
            .background(PixelPalette.Surface)
            .border(PixelSpace.Stroke, if (checked) PixelPalette.Primary else PixelPalette.BorderDark)
            .toggleable(
                value = checked,
                enabled = !added,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = appName
                // An inert row is disabled, so this is the only place a screen reader can
                // hear where removal does live — the visible line saying so is folded
                // into the merged node, not read as its own.
                stateDescription = when {
                    added -> "Added — remove it on Targets"
                    selected -> "Selected"
                    else -> "Not selected"
                }
            }
            .padding(PixelSpace.Snug),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same as a target row: the icon sits bare, because the row around it is already
        // the frame.
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
                androidx.compose.material3.Text(
                    "▣",
                    color = if (checked) PixelPalette.Primary else PixelPalette.Muted,
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
            if (added) {
                // The picker cannot uncheck; naming where removal does happen is what
                // keeps an inert row from reading as a broken one.
                androidx.compose.material3.Text(
                    text = "Already added — remove it on Targets.",
                    style = MonoTypeScale.Metadata,
                    color = TextSoft,
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(CheckBoxSize)
                .background(PixelPalette.Panel)
                .border(
                    PixelSpace.Stroke,
                    if (checked) PixelPalette.Primary else PixelPalette.BorderDark,
                ),
        ) {
            androidx.compose.material3.Text(
                text = if (checked) "✓" else "",
                // Same symbol-font treatment as a row's action glyph: the pixel face has
                // no `✓`, so naming it here would only promise a fallback.
                style = PixelTypeScale.RowGlyph,
                color = PixelPalette.Primary,
            )
        }
    }
}

/** The icon slot, at the same size as a target row's, so a picked app looks like a target. */
private val IconBoxSize = 40.dp

/** The row's checkbox decoration — small, since the whole row is the tap target. */
private val CheckBoxSize = 28.dp
