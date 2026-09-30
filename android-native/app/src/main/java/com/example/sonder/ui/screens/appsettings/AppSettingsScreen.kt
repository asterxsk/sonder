package com.example.sonder.ui.screens.appsettings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sonder.domain.BlockScope
import com.example.sonder.domain.ShortsCatalog
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelSpace
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.theme.TextSoft
import com.example.sonder.ui.kit.BadgeTone
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelConfirmDialog
import com.example.sonder.ui.kit.PixelHoldButton
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.PixelTabs
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Per-app access policy (§8, extended): one package's bank ceiling, its scope where the app
 * has a short-form surface, and the two destructive controls that need a wait to land.
 *
 * Every control here edits a draft. Nothing reaches the app until SAVE — which is itself a
 * two-press hold, because a limit is easy to loosen and hard to notice loosening. Leaving with
 * a pending draft asks first, on both the button and system back.
 *
 * REMOVE LIMIT sits under SAVE. It is the same thirty-second hold and it deletes the target
 * outright, and it is refused — with the wait named on the button — while the app is inside the
 * twelve hours that follow its bank running out.
 */
@Composable
fun AppSettingsScreen(
    packageName: String,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: AppSettingsViewModel = hiltViewModel(key = packageName),
) {
    LaunchedEffect(packageName) { viewModel.bind(packageName) }
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // The screen closes for both completed acts: a saved limit and a removed one. The SAVED
    // step is held on screen for a beat so the confirmation is read before the page goes.
    var completed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.finished.collect { finish ->
            when (finish) {
                AppSettingsFinish.SAVED -> {
                    completed = true
                    delay(SavedLingerMillis)
                }
                AppSettingsFinish.REMOVED -> Unit
            }
            onBack()
        }
    }

    var confirmingExit by remember { mutableStateOf(false) }
    val requestExit = {
        if (ui.dirty) confirmingExit = true else onBack()
    }
    // System back has to reach the same question as the button, or the guard is a decoration.
    // Disabled while the draft is clean, so back stays the plain navigation it has always been.
    BackHandler(enabled = ui.dirty) { confirmingExit = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(PixelSpace.Room),
    ) {
        PixelButton(
            text = "‹ TARGETS",
            style = PixelButtonStyle.SECONDARY,
            onClick = requestExit,
        )
        Spacer(Modifier.height(PixelSpace.Room))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                ui.label ?: packageName,
                style = PixelTypeScale.ScreenTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Primary,
                modifier = Modifier.weight(1f),
            )
            when {
                ui.dirty -> PixelStatusBadge(
                    tone = BadgeTone.COOLDOWN,
                    labelOverride = "UNSAVED",
                )
                ui.saved -> PixelStatusBadge(
                    tone = BadgeTone.GRANTED,
                    labelOverride = "SAVED",
                )
            }
        }
        Text(
            packageName,
            style = MonoTypeScale.PackageId,
            color = TextSoft,
        )
        Spacer(Modifier.height(PixelSpace.Section))

        PixelPanel(modifier = Modifier.fillMaxWidth()) {
            Column {
                // Shown only for an app with a known short-form surface. On any other app a
                // shorts-scope option could never fire, so offering it would be a setting
                // that lies about what it does.
                if (ShortsCatalog.isCatalogued(packageName)) {
                    ScopeControl(
                        current = ui.blockScope,
                        surfaceLabel = ShortsCatalog.surfaceLabelFor(packageName),
                        onSelect = viewModel::setBlockScope,
                    )
                    Spacer(Modifier.height(PixelSpace.Base))
                }
                MaxControl(current = ui.maxMillis, onSelect = viewModel::setMax)
            }
        }

        Spacer(Modifier.height(PixelSpace.Base))

        // SAVE is a two-press hold: the second press only counts once the fill has run out,
        // and the fill is cancelled by tapping it again. Same shape as REMOVE LIMIT below, and
        // deliberately so — the two controls that change the rule are the two that take thirty
        // seconds, and the one that changes nothing (the scope, the presets) takes none.
        if (completed) {
            PixelButton(
                text = "SAVED",
                style = PixelButtonStyle.SUCCESS,
                enabled = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            PixelHoldButton(
                text = "SAVE",
                holdMillis = ConfirmHoldMillis,
                onComplete = viewModel::save,
                enabled = ui.dirty,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(PixelSpace.Base))

        RemoveLimit(ui = ui, onRemove = viewModel::removeLimit)

        Spacer(Modifier.height(PixelSpace.Edge))
    }

    if (confirmingExit) {
        PixelConfirmDialog(
            title = "Discard changes?",
            body = "Your edits to this app's rule are not saved yet. Leaving now keeps the " +
                "rule as it was.",
            confirmText = "DISCARD",
            onConfirm = {
                confirmingExit = false
                onBack()
            },
            onDismiss = { confirmingExit = false },
        )
    }
}

/**
 * REMOVE LIMIT: the same thirty-second hold as SAVE, and the app stops being a target.
 *
 * While the twelve-hour removal lock is running the button is disabled and says so, with the
 * wait counting down on it. The lock is not a warning the user can click past: it is the price
 * of having spent the app down to nothing, and it exists precisely so that running out is not
 * answered by switching the rule off.
 */
@Composable
private fun RemoveLimit(ui: AppSettingsUiState, onRemove: () -> Unit) {
    val remaining = rememberLockRemaining(ui.removalLockedUntilMillis)

    if (ui.removalLocked) {
        PixelButton(
            text = "REMOVE LIMIT — LOCKED ${formatRemaining(remaining)}",
            style = PixelButtonStyle.DANGER,
            enabled = false,
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(PixelSpace.Snug))
        Text(
            text = "THIS APP RAN OUT OF TIME. ITS LIMIT CANNOT BE REMOVED UNTIL THE LOCK RUNS OUT," +
                " AND A WON HAND CLEARS IT EARLY.",
            style = MonoTypeScale.Metadata,
            color = TextSoft,
        )
        return
    }

    PixelHoldButton(
        text = "✕  REMOVE LIMIT",
        holdMillis = ConfirmHoldMillis,
        onComplete = onRemove,
        tone = PixelPalette.Danger,
        fillColor = PixelPalette.Danger,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(PixelSpace.Snug))
    Text(
        text = "REMOVING THE LIMIT STOPS THIS APP BEING GATED AT ALL. THE BANK GOES WITH IT.",
        style = MonoTypeScale.Metadata,
        color = TextSoft,
    )
}

/**
 * A second-by-second read of a deadline that is hours away.
 *
 * The ticker only runs while the deadline is in the future, and it stops there rather than
 * counting up: once the lock has run out the button behind it is the live one, and a screen
 * left open from before then re-reads the lock on the next bind.
 */
@Composable
private fun rememberLockRemaining(untilMillis: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMillis) {
        if (untilMillis <= System.currentTimeMillis()) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (untilMillis <= now) break
            delay(1_000)
        }
    }
    return (untilMillis - now).coerceAtLeast(0L)
}

/**
 * The scope control: what part of the app a target gates. Deliberately not a preset row — those
 * select among durations, and this is a choice between two kinds of target with no default to
 * fall back to. The description under it is the part that matters: "WHOLE APP" and the surface's
 * own name are not a severity dial, and the difference between blocking an app and blocking one
 * screen inside it has to be legible before the user picks one.
 *
 * @param surfaceLabel what this app calls its short-form surface ("REELS", "SHORTS"). It
 *   comes from [ShortsCatalog], which is also what decides whether this control is offered
 *   at all, so the tab and the sentence under it name one surface by the name its own app
 *   uses instead of a combined label that names a screen the app does not have.
 */
@Composable
private fun ScopeControl(
    current: BlockScope,
    surfaceLabel: String,
    onSelect: (BlockScope) -> Unit,
) {
    val options = listOf(BlockScope.WHOLE_APP, BlockScope.SHORTS_ONLY)
    Text(
        "WHAT IS BLOCKED",
        style = PixelTypeScale.SectionTitle,
        fontFamily = PixelFont,
        color = PixelPalette.Text,
    )
    Spacer(Modifier.height(PixelSpace.Snug))
    PixelTabs(
        tabs = listOf("WHOLE APP", surfaceLabel),
        selected = options.indexOf(current).coerceAtLeast(0),
        onSelect = { index -> onSelect(options[index]) },
    )
    Spacer(Modifier.height(PixelSpace.Snug))
    Text(
        when (current) {
            BlockScope.WHOLE_APP -> "The whole app is gated. Every screen needs a won hand."
            BlockScope.SHORTS_ONLY ->
                "Only ${surfaceLabel.lowercase().replaceFirstChar { it.uppercase() }} is " +
                    "gated. The rest of the app stays open."
        },
        style = MonoTypeScale.Metadata,
        color = TextSoft,
    )
}

/**
 * The bank ceiling: how much access this app can hold at once.
 *
 * One control rather than the five the old model had, because the bank is the whole of a rule
 * now — a win adds the stake up to this number, a loss takes the stake off, and time is spent
 * only by using the app. There is no OFF preset: a bank with no ceiling is not a bank, and an
 * app whose access never ran out would not need a gate at all.
 */
@Composable
private fun MaxControl(current: Long, onSelect: (Long) -> Unit) {
    val matched = BankSizePresets.indexOfFirst { it.value == current }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "BANK SIZE",
            style = PixelTypeScale.SectionTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Text,
        )
        Text(
            if (matched >= 0) "MAX" else "CUSTOM ${formatDuration(current)}",
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = PixelPalette.Muted,
        )
    }
    Spacer(Modifier.height(PixelSpace.Snug))
    PixelTabs(
        tabs = BankSizePresets.map { it.label },
        selected = matched,
        onSelect = { index -> onSelect(BankSizePresets[index].value) },
    )
    Spacer(Modifier.height(PixelSpace.Snug))
    Text(
        "THE MOST TIME THIS APP CAN HOLD. A WIN STOPS ADDING AT THIS NUMBER.",
        style = MonoTypeScale.Metadata,
        color = TextSoft,
    )
}

private data class Preset(val label: String, val value: Long)

private val BankSizePresets = listOf(
    Preset("30:00", 30 * 60_000L),
    Preset("1:00", 60 * 60_000L),
    Preset("2:00", 2 * 60 * 60_000L),
    Preset("3:00", 3 * 60 * 60_000L),
)

/** How long SAVE and REMOVE LIMIT hold before a second press counts. */
private const val ConfirmHoldMillis = 30_000L

/** How long SAVED stays on screen before the page closes onto Targets. */
private const val SavedLingerMillis = 600L

/** Millis to `H:MM:SS`, for a lock that is hours away. */
private fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}

/** Countdown readout for the locked button: short enough to sit inside a button. */
private fun formatRemaining(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d:%02d", totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60)
}
