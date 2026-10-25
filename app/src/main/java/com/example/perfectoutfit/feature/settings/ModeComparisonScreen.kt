package com.example.perfectoutfit.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.feature.recommendation.ModeComparisonLine
import kotlin.math.roundToInt

/** Feels-like vs actual: the overall line and one per activity type, numbers only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeComparisonScreen(
    onNavigateBack: () -> Unit,
    viewModel: ModeComparisonViewModel = hiltViewModel()
) {
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feels like vs. actual") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "A pair is two rated outfits of the same activity with exactly the same clothes, " +
                    "rated differently. It contradicts a temperature when the warmer one was rated " +
                    "colder; when both show the same degree it counts half. Lower is better.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            val current = comparison ?: return@Column
            SectionHeader("Overall")
            SettingsGroup({ ComparisonRow(rememberVectorPainter(Icons.Filled.Thermostat), "All activities", current.overall) })

            SectionHeader("By activity")
            SettingsGroup(
                *current.byActivityType.map { (activityType, line) ->
                    @Composable { ComparisonRow(painterResource(activityType.iconRes), activityType.displayName, line) }
                }.toTypedArray()
            )
            SectionHint(
                "A verdict needs at least ${ModeComparisonLine.MIN_PAIRS} pairs and a gap of " +
                    "${ModeComparisonLine.MIN_GAP_PERCENT} points."
            )
        }
    }
}

@Composable
private fun ComparisonRow(icon: Painter, title: String, line: ModeComparisonLine) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Text("Feels like ${percent(line.feelsLikeScore)}", style = MaterialTheme.typography.bodyLarge)
                    Text("Actual ${percent(line.actualScore)}", style = MaterialTheme.typography.bodyLarge)
                }
                Text(line.verdictText(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

private fun percent(score: Double?): String = score?.let { "${(it * 100).roundToInt()}%" } ?: "–"
