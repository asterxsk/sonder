package com.example.sonder.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sonder.domain.model.EnforcementState
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.BadgeTone
import com.example.sonder.ui.kit.PixelDock
import com.example.sonder.ui.kit.PixelPanel
import com.example.sonder.ui.kit.PixelStatusBadge
import com.example.sonder.ui.kit.TargetRow

/** Home: the console view of every target and its live state (§14 badges). */
@Composable
fun HomeScreen(
    onOpenTargets: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val targets by viewModel.targets.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Text(
            text = "SONDER",
            style = PixelTypeScale.Wordmark,
            fontFamily = PixelFont,
            color = PixelPalette.Primary,
        )
        Spacer(Modifier.height(4.dp))
        androidx.compose.material3.Text(
            text = "limits are earned back one hand at a time",
            style = MonoTypeScale.Metadata,
            color = PixelPalette.Muted,
        )
        Spacer(Modifier.height(16.dp))

        if (targets.isEmpty()) {
            PixelPanel(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.Text(
                        "NO TARGETS YET",
                        style = PixelTypeScale.SectionTitle,
                        fontFamily = PixelFont,
                        color = PixelPalette.Text,
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.Text(
                        "Pick the apps you want to gate\nbehind blackjack.",
                        style = MonoTypeScale.Body,
                        color = PixelPalette.Muted,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                items(targets, key = { it.packageName }) { t ->
                    PixelPanel {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Text(
                                    t.label,
                                    style = PixelTypeScale.SectionTitle,
                                    fontFamily = PixelFont,
                                    color = PixelPalette.Text,
                                )
                                when (t.state) {
                                    EnforcementState.GRANTED ->
                                        PixelStatusBadge(BadgeTone.GRANTED, labelOverride = "✓ ${t.remainingText}")
                                    EnforcementState.LOCKED ->
                                        PixelStatusBadge(BadgeTone.LOCKED, labelOverride = "▣ ${t.remainingText}")
                                    EnforcementState.IDLE ->
                                        PixelStatusBadge(BadgeTone.PLAYING)
                                    EnforcementState.DISABLED ->
                                        PixelStatusBadge(BadgeTone.COOLDOWN, labelOverride = "OFF")
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            androidx.compose.material3.Text(
                                t.packageName,
                                style = MonoTypeScale.PackageId,
                                color = PixelPalette.Muted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PixelDock(
            tabs = listOf("⌂" to "HOME", "◎" to "TARGETS", "▥" to "STATS", "⚙" to "SETTINGS"),
            selected = 0,
            onSelect = { index ->
                when (index) {
                    1 -> onOpenTargets()
                    2 -> onOpenStats()
                    3 -> onOpenSettings()
                }
            },
        )
    }
}
