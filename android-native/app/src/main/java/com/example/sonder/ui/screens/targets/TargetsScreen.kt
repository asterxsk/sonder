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
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelConfirmDialog
import com.example.sonder.ui.kit.PixelLoader
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelSkeletonList
import com.example.sonder.ui.kit.TargetRow
import com.example.sonder.ui.kit.TargetRowPending
import kotlinx.coroutines.delay

/**
 * Targets: the apps gated behind blackjack (§8). It opens on the added set only — no
 * tabs, no search — and ADD is the single way in. The dock, background, and system
 * insets come from the shared [PaddingValues]; there is no Back or DONE, so the list
 * keeps the screen height and the dock is the way out.
 *
 * Editing and removing are both delayed, and the countdown state lives here, not in
 * the ViewModel: exactly one pending action exists for the whole screen, so starting a
 * second wait replaces the first and two countdowns can never run side by side.
 */
@Composable
fun TargetsScreen(
    contentPadding: PaddingValues,
    onOpenAppSettings: (String) -> Unit,
    onAddApps: () -> Unit,
    viewModel: TargetsViewModel = hiltViewModel(),
) {
    val view by viewModel.ui.collectAsStateWithLifecycle()

    var pending by rememberSaveable(stateSaver = PendingTargetActionSaver) {
        mutableStateOf<PendingTargetAction?>(null)
    }

    /** The package whose removal wait was served and is now waiting on a second answer. */
    var confirming by rememberSaveable { mutableStateOf<String?>(null) }

    // Backgrounding cancels: a wait is only meant to survive while the user is looking
    // at the screen and can still tap the strip to back out. Nav3 dropping this entry
    // from composition handles "navigating away" instead, by cancelling the effect below.
    //
    // A configuration change is not backgrounding, though — ON_PAUSE fires for it too, and
    // clearing here would wipe the very state rememberSaveable just restored, which is how
    // rotating the device used to kill a running hold.
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && activity?.isChangingConfigurations != true) {
                pending = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keyed on the action's identity, not its remaining time, so a tick recomposes the
    // strip without restarting the loop. A new action (different package or kind) is a
    // new key, which cancels this effect and starts the fresh hold.
    val active = pending
    LaunchedEffect(active?.packageName, active?.kind) {
        val started = active ?: return@LaunchedEffect
        while (true) {
            delay(1_000L)
            // Re-read the live value instead of trusting the one this effect started
            // with. Cancelling is a write to `pending`, and while the composition is
            // paused — screen locked, app backgrounded — nothing recomposes to restart
            // this effect, so a captured value would let the loop run to completion and
            // fire an action the user had already backed out of.
            val live = pending ?: return@LaunchedEffect
            if (live.packageName != started.packageName || live.kind != started.kind) {
                return@LaunchedEffect
            }
            val next = live.tick()
            if (next == null) {
                // A served wait is permission to ask, not permission to act. The removal
                // lands on a second, explicit answer in red; only opening settings is
                // harmless enough to fire on the timer alone.
                when (live.kind) {
                    TargetActionKind.EDIT -> onOpenAppSettings(live.packageName)
                    TargetActionKind.REMOVE -> confirming = live.packageName
                }
                pending = null
                return@LaunchedEffect
            }
            pending = next
        }
    }

    // A tap on a control starts its wait; a tap on the same control again — the glyph,
    // or the strip that replaced it — cancels. Any other request replaces the first.
    // Remembered so the row callbacks below — and through them every row's own
    // buttons — keep one identity across recompositions. Only a write to `pending`
    // happens in here, and that state object outlives any recomposition, so a
    // remembered instance can never act on a stale read.
    val onRequest: (String, TargetActionKind) -> Unit = remember {
        { packageName, kind ->
            val current = pending
            pending = if (
                current != null && current.packageName == packageName && current.kind == kind
            ) {
                null
            } else {
                startPendingAction(packageName, kind)
            }
        }
    }
    val onEdit: (String) -> Unit = remember(onRequest) {
        { packageName -> onRequest(packageName, TargetActionKind.EDIT) }
    }
    val onRemove: (String) -> Unit = remember(onRequest) {
        { packageName -> onRequest(packageName, TargetActionKind.REMOVE) }
    }

    // The list and the removal question occupy the same ground: the panel replaces the
    // rows rather than floating over them, and the dock below is untouched either way.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = PixelSpace.Room),
        ) {
            Spacer(Modifier.height(PixelSpace.Room))
            androidx.compose.material3.Text(
                "TARGETS",
                style = PixelTypeScale.ScreenTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Primary,
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
                                onEdit = onEdit,
                                onRemove = onRemove,
                                pending = pending
                                    ?.takeIf { it.packageName == pick.packageName }
                                    ?.let { rowPending(it, pick.label, onRequest) },
                                iconBitmap = pick.icon,
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

        val asking = confirming
        if (asking != null) {
            // Named from the row the user acted on; the package id is the fallback for a
            // target that vanished while the question was open.
            val label = (view as? TargetsViewState.Rows)
                ?.picks
                ?.firstOrNull { it.packageName == asking }
                ?.label
                ?: asking
            PixelConfirmDialog(
                title = "Remove $label?",
                body = "It stops being limited. Its settings and its hands are kept, so " +
                    "adding it back restores them.",
                confirmText = "REMOVE",
                onConfirm = {
                    viewModel.remove(asking)
                    confirming = null
                },
                onDismiss = { confirming = null },
            )
        }
    }
}

/**
 * The running hold, as three Bundle-able values. Saving the data class directly is not
 * possible — a package name, an enum and a remaining-millis are not a Parcelable — and a
 * hold that vanishes on rotation reads as the app forgetting a decision the user is
 * partway through making. The remaining time is saved as-is rather than as a deadline, so
 * a restore can only keep counting down, never jump.
 */
private val PendingTargetActionSaver: Saver<PendingTargetAction?, Any> = Saver(
    save = { action ->
        action?.let { listOf(it.packageName, it.kind.name, it.remainingMillis) }
    },
    restore = { saved ->
        val parts = saved as List<*>
        PendingTargetAction(
            packageName = parts[0] as String,
            kind = TargetActionKind.valueOf(parts[1] as String),
            remainingMillis = parts[2] as Long,
        )
    },
)

/**
 * The row's view of a running countdown, kept here rather than in the kit so the row
 * learns what to draw and not what a delayed action is. The cancel is the same request
 * that started the wait: the screen reads a repeat of the same package and kind as
 * "back out", so "tap the control again" and "tap the strip" are one move.
 */
private fun rowPending(
    action: PendingTargetAction,
    appName: String,
    onRequest: (String, TargetActionKind) -> Unit,
): TargetRowPending {
    val seconds = action.remainingSeconds()
    return TargetRowPending(
        label = action.label(),
        description = when (action.kind) {
            TargetActionKind.EDIT -> "Edit $appName settings in ${seconds}s, tap to cancel"
            TargetActionKind.REMOVE -> "Remove $appName in ${seconds}s, tap to cancel"
        },
        tone = when (action.kind) {
            TargetActionKind.EDIT -> PixelPalette.Primary
            TargetActionKind.REMOVE -> PixelPalette.Danger
        },
        onClick = { onRequest(action.packageName, action.kind) },
    )
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
 * Framed explanatory state — a panel, not a blank list, so empty never reads as
 * frozen. [action] is the optional control a state needs; the others pass none.
 * Shared with the picker, whose states are the same family.
 */
@Composable
internal fun FramedNote(
    title: String,
    body: String,
    busy: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (busy) {
                PixelLoader()
                Spacer(Modifier.height(PixelSpace.Base))
            }
            androidx.compose.material3.Text(
                title,
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(PixelSpace.Snug))
            androidx.compose.material3.Text(
                body,
                style = MonoTypeScale.Body,
                color = TextSoft,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(PixelSpace.Room))
                action()
            }
        }
    }
}
