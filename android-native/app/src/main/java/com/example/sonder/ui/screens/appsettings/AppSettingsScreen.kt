package com.example.sonder.ui.screens.appsettings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.PixelTabs
import java.util.Locale

/**
 * Per-app access policy (§8, extended). One package's five knobs — each a segmented row
 * of presets — over its inherited values, plus a read-only view of the daily cap in use.
 * The label and package id head the screen; BACK returns to the Targets list, and system
 * back is the same move via the nav host.
 *
 * Every control here edits a draft. Nothing reaches the app until SAVE, which is what makes
 * "applied" something the screen can actually tell the user: the badge reads UNSAVED while
 * there is a difference and SAVED once a write has landed. Leaving with a pending draft
 * asks first, on both the button and system back — a save step that silently discards on
 * exit would be worse than the live writes it replaced.
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
    val rules = ui.rules
    val overrides = ui.overrides

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
            androidx.compose.material3.Text(
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
        androidx.compose.material3.Text(
            packageName,
            style = MonoTypeScale.PackageId,
            color = TextSoft,
        )
        Spacer(Modifier.height(PixelSpace.Section))

        PixelPanel(modifier = Modifier.fillMaxWidth()) {
            Column {
                // Shown only for an app with a known short-form surface. On any other app a
                // REELS & SHORTS option could never fire, so offering it would be a setting
                // that lies about what it does.
                if (ShortsCatalog.isCatalogued(packageName)) {
                    ScopeControl(
                        current = ui.blockScope,
                        onSelect = viewModel::setBlockScope,
                    )
                    Spacer(Modifier.height(PixelSpace.Base))
                }
                Knob(
                    title = "ACCESS PER WIN",
                    presets = WinGrantPresets,
                    current = rules.winGrantMillis,
                    custom = overrides.winGrantMillis != null,
                    onSelect = { value -> value?.let(viewModel::setWinGrant) },
                )
                Spacer(Modifier.height(PixelSpace.Base))
                Knob(
                    title = "LOSS PENALTY",
                    presets = LossPenaltyPresets,
                    current = rules.lossDebtMillis,
                    custom = overrides.lossDebtMillis != null,
                    onSelect = { value -> value?.let(viewModel::setLossDebt) },
                )
                Spacer(Modifier.height(PixelSpace.Base))
                Knob(
                    title = "DEBT CEILING",
                    presets = DebtCeilingPresets,
                    current = rules.maxDebtMillis,
                    custom = overrides.maxDebtMillis != null,
                    onSelect = { value -> value?.let(viewModel::setDebtCeiling) },
                )
                Spacer(Modifier.height(PixelSpace.Base))
                Knob(
                    title = "ABSENCE REVOKE",
                    presets = AbsenceRevokePresets,
                    current = rules.absenceRevokeMillis,
                    custom = overrides.absenceRevokeMillis != null,
                    onSelect = { value -> value?.let(viewModel::setAbsenceRevoke) },
                )
                Spacer(Modifier.height(PixelSpace.Base))
                Knob(
                    title = "DAILY CAP",
                    presets = DailyCapPresets,
                    current = rules.dailyCapMillis,
                    // The cap has no global default to inherit: null means unlimited, which
                    // is itself a chosen state, so this knob is always CUSTOM.
                    custom = true,
                    onSelect = { viewModel.setDailyCap(it) },
                )
            }
        }

        Spacer(Modifier.height(PixelSpace.Base))
        // Disabled while there is nothing to write, so the button's own state says whether
        // the screen holds unpublished edits — the badge says it in words, this says it in
        // the one control the user would reach for.
        PixelButton(
            text = "SAVE",
            style = PixelButtonStyle.PRIMARY,
            enabled = ui.dirty,
            onClick = viewModel::save,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(PixelSpace.Section))
        TodayLine(ui)
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
 * The scope control: what part of the app a target gates. Deliberately not a [Knob] — those
 * select among durations over an inherited default, and this is a choice between two kinds
 * of target with no default to fall back to. The description under it is the part that
 * matters: "WHOLE APP" and "REELS & SHORTS" are not a severity dial, and the difference
 * between blocking an app and blocking one screen inside it has to be legible before the
 * user picks one.
 */
@Composable
private fun ScopeControl(current: BlockScope, onSelect: (BlockScope) -> Unit) {
    val options = listOf(BlockScope.WHOLE_APP, BlockScope.SHORTS_ONLY)
    androidx.compose.material3.Text(
        "WHAT IS BLOCKED",
        style = PixelTypeScale.SectionTitle,
        fontFamily = PixelFont,
        color = PixelPalette.Text,
    )
    Spacer(Modifier.height(PixelSpace.Snug))
    PixelTabs(
        tabs = listOf("WHOLE APP", "REELS & SHORTS"),
        selected = options.indexOf(current).coerceAtLeast(0),
        onSelect = { index -> onSelect(options[index]) },
    )
    Spacer(Modifier.height(PixelSpace.Snug))
    androidx.compose.material3.Text(
        when (current) {
            BlockScope.WHOLE_APP -> "The whole app is gated. Every screen needs a won hand."
            BlockScope.SHORTS_ONLY ->
                "Only Reels and Shorts are gated. The rest of the app stays open."
        },
        style = MonoTypeScale.Metadata,
        color = TextSoft,
    )
}

/**
 * One knob: its name, whether the value is chosen (CUSTOM) or inherited (DEFAULT), and a
 * row of presets with the current one selected. [custom] must come from the raw stored
 * override, not a comparison of the effective value against the default: an effective
 * value that equals the default is ambiguous between chosen and inherited, and the label
 * exists precisely to tell those apart.
 */
@Composable
private fun Knob(
    title: String,
    presets: List<Preset>,
    current: Long?,
    custom: Boolean,
    onSelect: (Long?) -> Unit,
) {
    // A stored value that matches no preset lights no segment: indexOfFirst returns -1, and
    // a row with nothing lit reads as broken rather than as custom. That case is reachable
    // whenever the value in force is not one of the offered ones — an override written
    // before the presets changed, or a debug build's shortened default.
    //
    // It used to get a lit segment of its own, appended to the row. Six segments do not fit
    // the panel: the row is equal-weight, so adding one took each segment from 180px to
    // 150px and the labels wrapped to two lines ("2:0 / 0", "DEFAU / LT"). The readout
    // belongs in the status column instead, where it was already saying CUSTOM or DEFAULT
    // and only had to be extended to say *what*.
    val matched = presets.indexOfFirst { it.value == current }
    val status = if (custom) "CUSTOM" else "DEFAULT"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Text(
            title,
            style = PixelTypeScale.SectionTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Text,
        )
        androidx.compose.material3.Text(
            if (matched >= 0) status else "$status ${formatDuration(current ?: 0L)}",
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = if (custom) PixelPalette.Primary else PixelPalette.Muted,
        )
    }
    Spacer(Modifier.height(PixelSpace.Snug))
    PixelTabs(
        tabs = presets.map { it.label },
        selected = matched,
        onSelect = { index -> onSelect(presets[index].value) },
    )
}

/** The daily cap in force, as access granted today over the cap (or UNLIMITED). */
@Composable
private fun TodayLine(ui: AppSettingsUiState) {
    PixelPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            androidx.compose.material3.Text(
                "TODAY",
                style = PixelTypeScale.SectionTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Text,
            )
            Spacer(Modifier.height(PixelSpace.Snug))
            androidx.compose.material3.Text(
                "ACCESS GRANTED  ${formatDuration(ui.grantedTodayMillis)} / " +
                    (ui.rules.dailyCapMillis?.let(::formatDuration) ?: "UNLIMITED"),
                style = MonoTypeScale.Body,
                color = TextSoft,
            )
        }
    }
}

/** A preset's display label and the millis it writes; null means unlimited (daily cap OFF). */
private data class Preset(val label: String, val value: Long?)

private val WinGrantPresets = listOf(
    Preset("2:00", 2 * 60_000L),
    Preset("5:00", 5 * 60_000L),
    Preset("10:00", 10 * 60_000L),
    Preset("15:00", 15 * 60_000L),
    Preset("30:00", 30 * 60_000L),
)
private val LossPenaltyPresets = listOf(
    Preset("5:00", 5 * 60_000L),
    Preset("10:00", 10 * 60_000L),
    Preset("20:00", 20 * 60_000L),
    Preset("30:00", 30 * 60_000L),
)
private val DebtCeilingPresets = listOf(
    Preset("30:00", 30 * 60_000L),
    Preset("1:00", 60 * 60_000L),
    Preset("2:00", 2 * 60 * 60_000L),
)
private val AbsenceRevokePresets = listOf(
    Preset("0:30", 30_000L),
    Preset("1:00", 60_000L),
    Preset("2:00", 2 * 60_000L),
    Preset("5:00", 5 * 60_000L),
)
private val DailyCapPresets = listOf(
    Preset("OFF", null),
    Preset("30:00", 30 * 60_000L),
    Preset("1:00", 60 * 60_000L),
    Preset("2:00", 2 * 60 * 60_000L),
    Preset("3:00", 3 * 60 * 60_000L),
)

/** Millis to `H:MM:SS` above an hour, else `MM:SS`, for the TODAY line. */
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
