package com.example.perfectoutfit.feature.explorer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.perfectoutfit.core.model.BODY_PART_DISPLAY_ORDER
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.ui.components.ClothingItemChip
import com.example.perfectoutfit.ui.components.SportToggle
import com.example.perfectoutfit.ui.components.ratingLabel

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
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SportToggle(
                selectedSport = uiState.sport,
                onSportSelected = viewModel::selectSport
            )

            when {
                uiState.isLoading -> CircularProgressIndicator(
                    modifier = Modifier.padding(top = 24.dp)
                )
                uiState.stops.isEmpty() -> Text(
                    text = "Rate some outfits for ${uiState.sport.name.lowercase()
                        .replaceFirstChar { it.uppercase() }} to unlock the explorer.",
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
                        onValueChange = { viewModel.selectIndex(it.toInt()) },
                        valueRange = 0f..(uiState.stops.size - 1).coerceAtLeast(0).toFloat(),
                        steps = (uiState.stops.size - 2).coerceAtLeast(0),
                        enabled = uiState.stops.size > 1
                    )

                    uiState.recommendation?.let { recommendation ->
                        ExplorerRecommendationCard(recommendation)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExplorerRecommendationCard(recommendation: OutfitEntryWithDetails) {
    val itemsByBodyPart = recommendation.clothingItems.groupBy { it.bodyPart }
    val ratingText = ratingLabel(recommendation.entry.comfortRating)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "What you wore",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (ratingText.isNotEmpty()) {
                Text(
                    text = "Last time: $ratingText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            val notes = recommendation.entry.notes
            if (notes.isNotEmpty()) {
                Text(
                    text = "Outfit notes: $notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            BODY_PART_DISPLAY_ORDER.forEach { part ->
                val items = itemsByBodyPart[part]
                if (!items.isNullOrEmpty()) {
                    Text(
                        text = part.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items.forEach { item ->
                            ClothingItemChip(name = item.name)
                        }
                    }
                }
            }
        }
    }
}
