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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.ui.components.ActivityTypeSelector
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
    val filterActivityType by viewModel.filterActivityType.collectAsStateWithLifecycle()
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
            // No "All" chip; deselect to show all
            ActivityTypeSelector(
                selectedActivityType = filterActivityType,
                onActivityTypeSelected = viewModel::setFilter,
                onDeselect = { viewModel.setFilter(null) },
                modifier = Modifier.padding(vertical = 8.dp)
            )

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
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val entry = item.details.entry
    val date = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date(entry.createdAt))

    val emoji = ratingEmoji(entry.comfortRating)

    val secondaryText = if (selected)
        MaterialTheme.colorScheme.onSecondaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            // Neutral like Home's cards; only selection, a real mode change, gets a tint
            containerColor = if (selected)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CARD_MIN_HEIGHT)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Activity icon; in selection mode the selection circle takes its place,
            // both the same size so the card keeps its height when the mode changes.
            when {
                !selecting -> Icon(
                    painter = painterResource(entry.activityType.iconRes),
                    contentDescription = entry.activityType.displayName,
                    modifier = Modifier.size(LEADING_ICON_SIZE),
                    tint = secondaryText
                )
                selected -> Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    modifier = Modifier.size(LEADING_ICON_SIZE),
                    tint = MaterialTheme.colorScheme.primary
                )
                else -> Icon(
                    Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "Not selected",
                    modifier = Modifier.size(LEADING_ICON_SIZE),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = date,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Where it was logged, filling the space up to the temperature; long names are cut
            Text(
                text = item.details.weatherSnapshot.locationName,
                style = MaterialTheme.typography.bodyLarge,
                color = secondaryText,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Actual / feels like, always in that order; the value matching uses now stands out.
            // Fixed width so the columns line up whether it is 5° / 3° or −12° / −18°
            val inUseText = if (selected)
                MaterialTheme.colorScheme.onSecondaryContainer
            else
                MaterialTheme.colorScheme.onSurface
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = if (item.useApparent) secondaryText else inUseText)) {
                        append("${item.actualCelsius}°")
                    }
                    withStyle(SpanStyle(color = secondaryText)) { append(" / ") }
                    withStyle(SpanStyle(color = if (item.useApparent) inUseText else secondaryText)) {
                        append("${item.feelsLikeCelsius}°")
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.width(88.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Rating emoji, or a "Rate" label in primary if unrated
            if (emoji == null) {
                Text(
                    text = "Rate",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.width(32.dp)
                )
            } else {
                Text(
                    text = emoji,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(32.dp)
                )
            }
        }
    }
}

private const val DATE_PATTERN = "d MMM"
private val CARD_MIN_HEIGHT = 72.dp
private val LEADING_ICON_SIZE = 24.dp
