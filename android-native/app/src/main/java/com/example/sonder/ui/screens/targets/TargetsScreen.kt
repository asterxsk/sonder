package com.example.sonder.ui.screens.targets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.sonder.theme.MonoTypeScale
import com.example.sonder.theme.PixelFont
import com.example.sonder.theme.PixelPalette
import com.example.sonder.theme.PixelTypeScale
import com.example.sonder.ui.kit.PixelButton
import com.example.sonder.ui.kit.PixelButtonStyle
import com.example.sonder.ui.kit.PixelTabs
import com.example.sonder.ui.kit.TargetRow

/** Targets: inventory-style picker (§8 rows) with ALL / ON tabs. */
@Composable
fun TargetsScreen(
    onBack: () -> Unit,
    viewModel: TargetsViewModel = hiltViewModel(),
) {
    val picks by viewModel.picks.collectAsState()
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }

    val filtered = picks
        .filter { if (tab == 1) it.enabled else true }
        .filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PixelPalette.Bg)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PixelButton(text = "←", onClick = onBack, minHeight = 40.dp)
            Spacer(Modifier.height(0.dp))
            androidx.compose.material3.Text(
                "TARGETS",
                style = PixelTypeScale.ScreenTitle,
                fontFamily = PixelFont,
                color = PixelPalette.Primary,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { androidx.compose.material3.Text("search apps…", style = MonoTypeScale.Body, color = PixelPalette.Muted) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MonoTypeScale.Body,
        )
        Spacer(Modifier.height(12.dp))

        PixelTabs(
            tabs = listOf("ALL", "LIMITED (${picks.count { it.enabled }})"),
            selected = tab,
            onSelect = { tab = it },
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.packageName }) { pick ->
                TargetRow(
                    appName = pick.label,
                    packageName = pick.packageName,
                    enabled = pick.enabled,
                    iconGlyph = if (pick.enabled) "♠" else "▣",
                    onClick = { viewModel.toggle(pick.packageName, pick.label, !pick.enabled) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            "Toggling ON means opening that app requires winning a hand of blackjack.",
            style = MonoTypeScale.Metadata,
            color = PixelPalette.Muted,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        PixelButton(
            text = "DONE",
            style = PixelButtonStyle.SECONDARY,
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
