package com.example.sonder.ui.screens.appsettings

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelTabs
import java.util.Locale

/**
 * Per-app access policy (§8, extended). One package's five knobs — each a segmented row
 * of presets — over its inherited values, plus a read-only view of the daily cap in use.
 * The label and package id head the screen; BACK returns to the Targets list, and system
 * back is the same move via the nav host.
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
            onClick = onBack,
        )
        Spacer(Modifier.height(PixelSpace.Room))

        androidx.compose.material3.Text(
            ui.label ?: packageName,
            style = PixelTypeScale.ScreenTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        androidx.compose.material3.Text(
            packageName,
            style = MonoTypeScale.PackageId,
            color = TextSoft,
        )
        Spacer(Modifier.height(PixelSpace.Section))

        PixelPanel(modifier = Modifier.fillMaxWidth()) {
            Column {
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

        Spacer(Modifier.height(PixelSpace.Section))
        TodayLine(ui)
        Spacer(Modifier.height(PixelSpace.Edge))
    }
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
            if (custom) "CUSTOM" else "DEFAULT",
            style = PixelTypeScale.Badge,
            fontFamily = PixelFont,
            color = if (custom) PixelPalette.Primary else PixelPalette.Muted,
        )
    }
    Spacer(Modifier.height(PixelSpace.Snug))
    PixelTabs(
        tabs = presets.map { it.label },
        selected = presets.indexOfFirst { it.value == current },
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
