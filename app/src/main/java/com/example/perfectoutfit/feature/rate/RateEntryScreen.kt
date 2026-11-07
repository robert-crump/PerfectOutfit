package com.example.perfectoutfit.feature.rate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.headToToeGroups
import com.example.perfectoutfit.core.model.inHeadToToeOrder
import com.example.perfectoutfit.core.model.roundedReferenceTemp
import com.example.perfectoutfit.ui.components.CardTitleRow
import com.example.perfectoutfit.ui.components.ComfortRatingBar
import com.example.perfectoutfit.ui.components.WeatherCard
import com.example.perfectoutfit.ui.components.entryDateLabel
import com.example.perfectoutfit.ui.components.neutralCardColors
import com.example.perfectoutfit.ui.components.verticalScrollbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Rating (and editing) a logged outfit; [highlightRating] flashes the rating card (from the reminder). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RateEntryScreen(
    onNavigateBack: () -> Unit,
    highlightRating: Boolean = false,
    externalCancelRequested: Boolean = false,
    onExternalCancelConfirmed: () -> Unit = {},
    onExternalCancelDismissed: () -> Unit = {},
    viewModel: RateEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableItems by viewModel.availableItems.collectAsStateWithLifecycle()

    val requestLeave = rememberDiscardGuard(
        hasUnsavedWork = uiState.hasUnsavedEdits,
        title = "Discard changes?",
        text = "Your changes to this outfit will be lost. Are you sure?",
        onLeave = onNavigateBack,
        externalCancelRequested = externalCancelRequested,
        onExternalCancelConfirmed = onExternalCancelConfirmed,
        onExternalCancelDismissed = onExternalCancelDismissed
    )
    BackHandler(onBack = requestLeave)

    if (uiState.isLoading) {
        OutfitLoadingScaffold(title = "Rate Outfit", onBack = requestLeave)
        return
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    val scrollState = rememberScrollState()
    var flashAlpha by remember { mutableStateOf(0f) }
    val animatedAlpha by animateFloatAsState(
        targetValue = flashAlpha,
        animationSpec = tween(durationMillis = 400),
        label = "rating_flash"
    )

    LaunchedEffect(highlightRating) {
        if (highlightRating) {
            delay(300)
            scrollState.animateScrollTo(scrollState.maxValue)
            flashAlpha = 0.35f
            delay(700)
            flashAlpha = 0f
        }
    }

    val edits = uiState.edits
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rate Outfit", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = requestLeave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::save, enabled = edits.itemIds.isNotEmpty()) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        var showItemsSheet by rememberSaveable { mutableStateOf(false) }
        val selectedItems = availableItems.filter { it.id in edits.itemIds }.inHeadToToeOrder()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .imePadding()
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GeneralCard(uiState)

            DetailCard(
                title = "Items worn",
                action = { TextButton(onClick = { showItemsSheet = true }) { Text("Edit") } }
            ) {
                if (selectedItems.isEmpty()) {
                    Text(
                        "No items selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedItems.forEach { WornItemPill(it.name) }
                    }
                }
            }

            DetailCard(title = "Notes") {
                // Bare field, so its text lines up with the card title instead of TextField's inset.
                BasicTextField(
                    value = edits.notes,
                    onValueChange = viewModel::setNotes,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (edits.notes.isEmpty()) {
                                Text(
                                    "Tap to add notes…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }

            // Flashes when opened from the rating reminder, to point at what's being asked.
            val cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
            DetailCard(
                title = "How did it feel?",
                containerColor = MaterialTheme.colorScheme.primaryContainer
                    .copy(alpha = animatedAlpha)
                    .compositeOver(cardColor)
            ) {
                ComfortRatingBar(
                    selectedRating = edits.comfortRating,
                    onRatingSelected = viewModel::setRating
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        if (showItemsSheet) {
            ItemsWornSheet(
                availableItems = availableItems,
                selectedItemIds = edits.itemIds,
                onToggle = viewModel::toggleItem,
                onDismiss = { showItemsSheet = false }
            )
        }
    }
}

/** The workout's weather, in Home's weather card; the date and hour stand where Home says "Now". */
@Composable
private fun GeneralCard(uiState: RateEntryUiState) {
    val ws = uiState.weatherSnapshot
    // "Sun, 12 Oct · 14:00": the workout hour the outfit and weather were for.
    val timeLabel = listOfNotNull(
        uiState.workoutTime?.let { entryDateLabel(it.toLocalDate(), uiState.today, withWeekday = true) },
        uiState.workoutTime?.format(timeFormatter)
    ).joinToString(" · ").ifEmpty { null }

    if (ws != null) {
        // No hints: they advise on what to pack now, not on a past workout.
        WeatherCard(
            displayTemp = ws.roundedReferenceTemp(uiState.useApparentTemperature),
            weather = ws,
            useApparentTemperature = uiState.useApparentTemperature,
            locationName = ws.locationName,
            timeLabel = timeLabel
        )
    } else if (timeLabel != null) {
        Card(modifier = Modifier.fillMaxWidth(), colors = neutralCardColors()) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

/** A detail card: a [CardTitleRow] (with an optional action at its end), then content. */
@Composable
private fun DetailCard(
    title: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        CardTitleRow(title = title, action = action)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            content()
        }
    }
}

/** A worn item, read-only: editing happens in [ItemsWornSheet]. */
@Composable
private fun WornItemPill(name: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Every item on offer, by body part; a tap toggles it on the entry straight away. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemsWornSheet(
    availableItems: List<ClothingItem>,
    selectedItemIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Items worn", style = MaterialTheme.typography.titleLarge)
            availableItems.headToToeGroups { it.bodyPart }.forEach { (bodyPart, items) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        bodyPart.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ItemChipGroup(items, selectedItemIds, onToggle)
                }
            }
            Button(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Done") }
        }
    }
}
