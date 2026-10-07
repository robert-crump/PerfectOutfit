package com.example.perfectoutfit.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.ui.components.ratingEmoji
import com.example.perfectoutfit.ui.components.verticalScrollbar
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val selecting = selectedIds.isNotEmpty()
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    BackHandler(enabled = selecting, onBack = viewModel::clearSelection)

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text("${selectedIds.size} selected") },
                    actions = {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete selected")
                        }
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    windowInsets = WindowInsets(0)
                )
            } else {
                TopAppBar(
                    title = { Text("Outfit History") },
                    windowInsets = WindowInsets(0)
                )
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
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

            val loadedEntries = entries
            if (loadedEntries == null) {
                // Still waiting on the first database emission — show nothing
                // rather than flashing the empty-state message.
            } else if (loadedEntries.isEmpty()) {
                Text(
                    text = "No outfit entries yet. Rate an outfit to see it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp)
                )
            } else {
                val lazyListState = rememberLazyListState()
                val dateWidth = rememberDateWidth()
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScrollbar(lazyListState)
                ) {
                    items(loadedEntries, key = { it.details.entry.id }) { item ->
                        val id = item.details.entry.id
                        HistoryCard(
                            item = item,
                            selecting = selecting,
                            selected = id in selectedIds,
                            dateWidth = dateWidth,
                            onClick = {
                                if (selecting) viewModel.toggleSelection(id)
                                else onNavigateToRateOutfit(id)
                            },
                            onLongClick = {
                                if (!selecting) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.toggleSelection(id)
                                }
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }
        }
        if (!selecting) {
            ExtendedFloatingActionButton(
                onClick = onNavigateToNewOutfit,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Outfit") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }
        }
    }

    if (confirmingDelete) {
        val count = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text(if (count == 1) "Delete 1 entry?" else "Delete $count entries?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.deleteSelected()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    item: HistoryItem,
    selecting: Boolean,
    selected: Boolean,
    dateWidth: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = item.details
    val date = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date(entry.entry.createdAt))

    val isUnrated = entry.entry.comfortRating == null
    val emoji = ratingEmoji(entry.entry.comfortRating)

    val outfitText = if (entry.clothingItems.isNotEmpty())
        entry.clothingItems.joinToString(", ") { it.name }
    else
        "No items"

    val secondaryText = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.primaryContainer
                isUnrated -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Date column, one line wide enough for any month ("23 Juni", "28 Sept.").
            // In selection mode the selection circle takes its place; the minimum
            // height keeps the card from shrinking or growing when the mode changes.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .width(dateWidth)
                    .heightIn(min = SELECTION_ICON_SIZE)
            ) {
                if (selecting) {
                    if (selected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = "Selected",
                            modifier = Modifier.size(SELECTION_ICON_SIZE),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Icon(
                            Icons.Outlined.RadioButtonUnchecked,
                            contentDescription = "Not selected",
                            modifier = Modifier.size(SELECTION_ICON_SIZE),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = date,
                        style = DateStyle(),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Fixed width so the emojis line up whether it is 5°C or −12°C
            Text(
                text = "${item.temperatureCelsius}°C",
                style = MaterialTheme.typography.bodyMedium,
                color = secondaryText,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.width(52.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

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
                    text = "–",
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
                color = secondaryText,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private const val DATE_PATTERN = "d MMM"
private val SELECTION_ICON_SIZE = 24.dp

@Composable
private fun DateStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)

/** Width of the widest date this locale can produce (a two-digit day with each month), at the current font scale. */
@Composable
private fun rememberDateWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = DateStyle()
    val density = LocalDensity.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(measurer, style, density, locale) {
        val format = SimpleDateFormat(DATE_PATTERN, locale)
        val widest = (Calendar.JANUARY..Calendar.DECEMBER).maxOf { month ->
            val date = Calendar.getInstance().apply { set(2000, month, 28) }.time
            measurer.measure(format.format(date), style, maxLines = 1).size.width
        }
        with(density) { widest.toDp() }
    }
}
