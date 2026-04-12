package com.example.perfectoutfit.feature.history

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.ui.components.ratingEmoji
import com.example.perfectoutfit.ui.components.verticalScrollbar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToRateOutfit: (Long) -> Unit,
    onNavigateToNewOutfit: () -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val filterSport by viewModel.filterSport.collectAsStateWithLifecycle()
    val lastDeletedEntry by viewModel.lastDeletedEntry.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val restoredVersions = remember { mutableStateMapOf<Long, Int>() }

    LaunchedEffect(lastDeletedEntry) {
        val entry = lastDeletedEntry ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Entry deleted",
            actionLabel = "UNDO",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            restoredVersions[entry.entry.id] = (restoredVersions[entry.entry.id] ?: 0) + 1
            viewModel.undoDelete()
        } else {
            viewModel.clearLastDeleted()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        val isSnackbarVisible = snackbarHostState.currentSnackbarData != null
        val fabBottomPadding by animateDpAsState(
            targetValue = if (isSnackbarVisible) 68.dp else 16.dp,
            label = "fab_bottom_padding"
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text("Outfit History", style = MaterialTheme.typography.headlineMedium)

            // Sport filter chips — no "All" button; deselect to show all
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Sport.entries.forEach { sport ->
                    val iconRes = when (sport) {
                        Sport.CYCLING -> R.drawable.ic_bike
                        Sport.RUNNING -> R.drawable.ic_sprint
                    }
                    FilterChip(
                        selected = filterSport == sport,
                        onClick = {
                            viewModel.setFilter(if (filterSport == sport) null else sport)
                        },
                        label = { Text(sport.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        leadingIcon = if (filterSport == sport) {
                            {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }

            if (entries.isEmpty()) {
                Text(
                    text = "No outfit entries yet. Rate an outfit to see it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp)
                )
            } else {
                val lazyListState = rememberLazyListState()
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScrollbar(lazyListState)
                ) {
                    items(entries, key = { "${it.entry.id}_${restoredVersions[it.entry.id] ?: 0}" }) { entry ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { totalDistance -> totalDistance * 0.40f },
                            confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled) {
                                    viewModel.deleteEntry(entry)
                                    true
                                } else false
                            }
                        )
                        SwipeToDismissBox(
                            state = dismissState,
                            modifier = Modifier.animateItem().clipToBounds(),
                            backgroundContent = {
                                SwipeBackground(dismissState = dismissState)
                            }
                        ) {
                            HistoryCard(
                                entry = entry,
                                onClick = { onNavigateToRateOutfit(entry.entry.id) }
                            )
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNavigateToNewOutfit,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Outfit") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = fabBottomPadding, end = 16.dp)
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(dismissState: SwipeToDismissBoxState) {
    val progress = dismissState.progress
    val targetValue = dismissState.targetValue
    // Show red as soon as the user drags even a little
    val alpha = (progress * 5f).coerceIn(0f, 1f)
    val color = if (progress > 0f)
        Color(0xFFE53935).copy(alpha = alpha)
    else
        Color.Transparent
    val alignment = when (targetValue) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        else -> Alignment.CenterEnd
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = alignment
    ) {
        if (progress > 0f) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = Color.White.copy(alpha = alpha)
            )
        }
    }
}

@Composable
private fun HistoryCard(
    entry: OutfitEntryWithDetails,
    onClick: () -> Unit
) {
    val date = Date(entry.entry.createdAt)
    val dayFormat = SimpleDateFormat("d", Locale.getDefault())
    val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    val day = dayFormat.format(date)
    val month = monthFormat.format(date)

    val isUnrated = entry.entry.comfortRating == null
    val emoji = ratingEmoji(entry.entry.comfortRating)

    val outfitText = if (entry.clothingItems.isNotEmpty())
        entry.clothingItems.joinToString(", ") { it.name }
    else
        "No items"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnrated)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date column — wide enough for four-letter month abbreviations
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(48.dp)
            ) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = month,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Rating emoji (or bold hyphen if unrated)
            if (emoji != null) {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )
            } else {
                Text(
                    text = "\u2013",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Outfit items
            Text(
                text = outfitText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            // Edit icon
            IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
