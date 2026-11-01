package com.example.perfectoutfit.feature.explorer

import androidx.compose.foundation.layout.Box
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

/**
 * Each tagged item's range of perfect temperatures, above a strip showing which degrees have a
 * perfect outfit at all, which doubles as the x-axis (see "Temperature chart" in CONTEXT.md).
 */
@OptIn(ExperimentalMaterial3Api::class)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            ActivityTypeSelector(
                selectedActivityType = uiState.activityType,
                onActivityTypeSelected = viewModel::selectActivityType
            )

            when {
                uiState.isLoading || chart == null -> CircularProgressIndicator(
                    modifier = Modifier.padding(top = 48.dp)
                )
                !chart.hasRatedHistory -> Text(
                    text = "Rate some outfits for ${uiState.activityType.displayName} to unlock the explorer.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp)
                )
                else -> {
                    // Rows scroll; the x-axis stays put underneath them. Touching anywhere
                    // above it moves the selection too (vertical drags still scroll).
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .degreeSelection(chart, toggleOnTap = false, onSelect = viewModel::selectTemp)
                    ) {
                        if (chart.groups.isEmpty()) {
                            Text(
                                text = "No perfect outfits yet — rate one as perfect to see item ranges",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        } else {
                            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                                items(chart.groups.flatMap { it.rows }, key = { it.item.id }) { row ->
                                    ItemRangeRow(row, chart, selectedTemp)
                                }
                            }
                        }
                    }
                    CoverageAxis(
                        chart = chart,
                        selectedTemp = selectedTemp,
                        onSelect = viewModel::selectTemp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}
