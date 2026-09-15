package com.example.sonder.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelPanel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Stats: wins/losses ledger + recent hand history. Quiet, not dashboard-y (§21). */
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val hands by viewModel.recentHands.collectAsState()
    val wins by viewModel.wins.collectAsState()
    val losses by viewModel.losses.collectAsState()
    val timeFmt = remember { SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelButton(text = "←", onClick = onBack, minHeight = 40.dp)
            androidx.compose.material3.Text(
                "STATS",
                style = PixelTypeScale.ScreenTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            PixelPanel(modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text("✓", color = PixelPalette.Success)
                    androidx.compose.material3.Text(
                        "$wins",
                        style = PixelTypeScale.Timer,
                        fontFamily = PixelFont,
                        color = PixelPalette.Success,
                    )
                    androidx.compose.material3.Text("WINS", style = PixelTypeScale.Badge, fontFamily = PixelFont, color = PixelPalette.Muted)
                }
            }
            PixelPanel(modifier = Modifier.weight(1f)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.Text("▣", color = PixelPalette.Danger)
                    androidx.compose.material3.Text(
                        "$losses",
                        style = PixelTypeScale.Timer,
                        fontFamily = PixelFont,
                        color = PixelPalette.Danger,
                    )
                    androidx.compose.material3.Text("LOSSES", style = PixelTypeScale.Badge, fontFamily = PixelFont, color = PixelPalette.Muted)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Text(
            "RECENT HANDS",
            style = PixelTypeScale.SectionTitle,
            fontFamily = PixelFont,
            color = PixelPalette.Text,
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            items(hands, key = { it.id }) { hand ->
                PixelPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    ) {
                        Column {
                            androidx.compose.material3.Text(
                                hand.packageName.substringAfterLast('.'),
                                style = PixelTypeScale.SectionTitle,
                                fontFamily = PixelFont,
                                color = PixelPalette.Text,
                            )
                            androidx.compose.material3.Text(
                                timeFmt.format(Date(hand.playedAtMillis)),
                                style = MonoTypeScale.PackageId,
                                color = PixelPalette.Muted,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            androidx.compose.material3.Text(
                                when (hand.outcome) {
                                    "WIN" -> "✓ WIN"
                                    "LOSE" -> "▣ LOSS"
                                    else -> "— PUSH"
                                },
                                style = PixelTypeScale.Badge,
                                fontFamily = PixelFont,
                                color = when (hand.outcome) {
                                    "WIN" -> PixelPalette.Success
                                    "LOSE" -> PixelPalette.Danger
                                    else -> PixelPalette.Muted
                                },
                            )
                            androidx.compose.material3.Text(
                                "debt ${hand.debtAfterMillis / 60_000}m",
                                style = MonoTypeScale.PackageId,
                                color = PixelPalette.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}
