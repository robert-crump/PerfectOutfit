package com.example.perfectoutfit.feature.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.ui.components.ActivityTypeSelector
import com.example.perfectoutfit.ui.components.OutfitCard
import com.example.perfectoutfit.ui.components.TemperatureModeToggle
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    onNavigateBack: () -> Unit,
    viewModel: ExplorerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                // A warm-weather outfit fits on screen; a winter one runs past it.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            ActivityTypeSelector(
                selectedActivityType = uiState.activityType,
                onActivityTypeSelected = viewModel::selectActivityType
            )

            TemperatureModeToggle(
                useApparent = uiState.useApparent,
                onUseApparentSelected = viewModel::selectTemperatureMode
            )

            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.padding(top = 24.dp)
                )
                uiState.stops.isEmpty() -> Text(
                    text = "Rate some outfits for ${uiState.activityType.displayName} to unlock the explorer.",
                    style = MaterialTheme.typography.bodyLarge
                )
                else -> {
                    val temp = uiState.selectedTemp
                    if (temp != null) {
                        Text(
                            text = "$temp°C",
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }

                    Slider(
                        value = uiState.selectedIndex.toFloat(),
                        onValueChange = { viewModel.selectIndex(it.roundToInt()) },
                        valueRange = 0f..(uiState.stops.size - 1).coerceAtLeast(0).toFloat(),
                        steps = (uiState.stops.size - 2).coerceAtLeast(0),
                        enabled = uiState.stops.size > 1
                    )

                    if (uiState.stops.size > 1) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${uiState.stops.first()}°C",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = "${uiState.stops.last()}°C",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }

                    uiState.recommendation?.let { recommendation ->
                        OutfitCard(title = "What you wore", outfit = recommendation)
                    }
                }
            }
        }
    }
}
