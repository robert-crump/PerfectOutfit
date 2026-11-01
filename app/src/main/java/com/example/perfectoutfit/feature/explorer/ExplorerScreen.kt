package com.example.perfectoutfit.feature.explorer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.ui.components.ActivityTypeSelector
import com.example.perfectoutfit.ui.components.TemperatureModeToggle

/**
 * Each tagged item's range of perfect temperatures, under a strip showing which degrees have a
 * perfect outfit at all (see "Temperature chart" in CONTEXT.md).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExplorerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chart = uiState.chart
    val selectedTemp = uiState.selectedTemp

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Outfit Explorer") },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    ActivityTypeSelector(
                        selectedActivityType = uiState.activityType,
                        onActivityTypeSelected = viewModel::selectActivityType
                    )
                    TemperatureModeToggle(
                        useApparent = uiState.useApparent,
                        onUseApparentSelected = viewModel::selectTemperatureMode
                    )
                }
            }

            when {
                uiState.isLoading || chart == null -> item {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 48.dp))
                }
                !chart.hasRatedHistory -> item {
                    Text(
                        text = "Rate some outfits for ${uiState.activityType.displayName} to unlock the explorer.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                }
                else -> {
                    // The strip and axis stay put while the item rows scroll under them.
                    stickyHeader {
                        CoverageHeader(
                            chart = chart,
                            selectedTemp = selectedTemp,
                            onSelect = viewModel::selectTemp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(top = 24.dp)
                        )
                    }
                    if (chart.groups.isEmpty()) {
                        item {
                            Text(
                                text = "No perfect outfits yet — rate one as perfect to see item ranges",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                    chart.groups.forEach { group ->
                        item(key = "header-${group.bodyPart}") {
                            BodyPartHeader(group.bodyPart, chart, selectedTemp)
                        }
                        items(group.rows, key = { "item-${it.item.id}" }) { row ->
                            ItemRangeRow(row, chart, selectedTemp)
                        }
                    }
                    if (chart.notYetPerfect.isNotEmpty()) {
                        item { NotYetPerfect(chart.notYetPerfect) }
                    }
                }
            }
        }
    }
}
