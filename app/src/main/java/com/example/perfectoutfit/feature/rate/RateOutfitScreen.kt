package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.Place

import android.app.DatePickerDialog
import com.example.perfectoutfit.core.model.roundedReferenceTemp
import com.example.perfectoutfit.core.model.roundedTemp
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.clickable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.R
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.compositeOver
import com.example.perfectoutfit.core.model.headToToeGroups
import com.example.perfectoutfit.core.model.inHeadToToeOrder
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.ui.components.CardTitleRow
import com.example.perfectoutfit.ui.components.WeatherCard
import com.example.perfectoutfit.ui.components.neutralCardColors
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.ui.components.entryDateLabel
import com.example.perfectoutfit.ui.components.ComfortRatingBar
import com.example.perfectoutfit.ui.components.verticalScrollbar
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RateOutfitScreen(
    onNavigateBack: () -> Unit,
    highlightRating: Boolean = false,
    externalCancelRequested: Boolean = false,
    onExternalCancelConfirmed: () -> Unit = {},
    onExternalCancelDismissed: () -> Unit = {},
    viewModel: RateOutfitViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableItems by viewModel.availableItems.collectAsStateWithLifecycle()

    if (uiState.isLoading) {
        // Nothing entered yet, so a tab switch needs no confirmation.
        LaunchedEffect(externalCancelRequested) {
            if (externalCancelRequested) onExternalCancelConfirmed()
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (uiState.mode is OutfitScreenMode.RateExisting) "Rate Outfit" else "Log Outfit",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    },
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
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
        return
    }

    if (uiState.mode !is OutfitScreenMode.RateExisting) {
        LogOutfitWizard(
            uiState = uiState,
            availableItems = availableItems,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            externalCancelRequested = externalCancelRequested,
            onExternalCancelConfirmed = onExternalCancelConfirmed,
            onExternalCancelDismissed = onExternalCancelDismissed
        )
    } else {
        RateExistingOutfitContent(
            uiState = uiState,
            availableItems = availableItems,
            viewModel = viewModel,
            onNavigateBack = onNavigateBack,
            highlightRating = highlightRating,
            externalCancelRequested = externalCancelRequested,
            onExternalCancelConfirmed = onExternalCancelConfirmed,
            onExternalCancelDismissed = onExternalCancelDismissed
        )
    }
}

// ─── Rate existing outfit ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RateExistingOutfitContent(
    uiState: RateOutfitUiState,
    availableItems: List<ClothingItem>,
    viewModel: RateOutfitViewModel,
    onNavigateBack: () -> Unit,
    highlightRating: Boolean = false,
    externalCancelRequested: Boolean = false,
    onExternalCancelConfirmed: () -> Unit = {},
    onExternalCancelDismissed: () -> Unit = {}
) {
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(externalCancelRequested) {
        if (externalCancelRequested) {
            if (uiState.hasUnsavedEdits) viewModel.showDismissDialog() else onExternalCancelConfirmed()
        }
    }

    if (uiState.showDismissDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.hideDismissDialog()
                onExternalCancelDismissed()
            },
            title = { Text("Discard changes?") },
            text = { Text("Your changes to this outfit will be lost. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    // Same as the log wizard: let the dialog's exit animation finish first.
                    viewModel.hideDismissDialog()
                    coroutineScope.launch {
                        delay(300)
                        onExternalCancelConfirmed()
                    }
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.hideDismissDialog()
                    onExternalCancelDismissed()
                }) { Text("Keep editing") }
            }
        )
    }

    val scrollState = rememberScrollState()
    var flashAlpha by remember { mutableStateOf(0f) }
    val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = flashAlpha,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
        label = "rating_flash"
    )

    LaunchedEffect(highlightRating, uiState.isLoading) {
        if (highlightRating && !uiState.isLoading) {
            delay(300)
            scrollState.animateScrollTo(scrollState.maxValue)
            flashAlpha = 0.35f
            delay(700)
            flashAlpha = 0f
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rate Outfit", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::save,
                        enabled = uiState.selectedItemIds.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        var showItemsSheet by rememberSaveable { mutableStateOf(false) }
        val selectedItems = availableItems
            .filter { it.id in uiState.selectedItemIds }
            .inHeadToToeOrder()

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
                    value = uiState.notes,
                    onValueChange = viewModel::setNotes,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        Box {
                            if (uiState.notes.isEmpty()) {
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
                    selectedRating = uiState.comfortRating,
                    onRatingSelected = viewModel::setRating
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        if (showItemsSheet) {
            ItemsWornSheet(
                availableItems = availableItems,
                selectedItemIds = uiState.selectedItemIds,
                onToggle = viewModel::toggleItem,
                onDismiss = { showItemsSheet = false }
            )
        }
    }
}

/** The workout's weather, in Home's weather card; the date and hour stand where Home says "Now". */
@Composable
private fun GeneralCard(uiState: RateOutfitUiState) {
    val ws = uiState.weatherSnapshot
    // "Sun, 12 Oct · 14:00": the workout hour the outfit and weather were for.
    val timeLabel = listOfNotNull(
        uiState.workoutTime?.let { entryDateLabel(it.toLocalDate(), LocalDate.now(), withWeekday = true) },
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ItemsWornSheet(
    availableItems: List<ClothingItem>,
    selectedItemIds: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val colors = outfitChipColorSet()
    val chipBorder = FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = false,
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = Color.Transparent
    )
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
                run {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            bodyPart.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items.forEach { item ->
                                val isSelected = item.id in selectedItemIds
                                val bg = if (isSelected) colors.activeBg else colors.inactiveBg
                                val label = if (isSelected) colors.activeLabel else colors.inactiveLabel
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onToggle(item.id) },
                                    label = { Text(item.name) },
                                    border = chipBorder,
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = bg,
                                        selectedContainerColor = bg,
                                        labelColor = label,
                                        selectedLabelColor = label
                                    )
                                )
                            }
                        }
                    }
                }
            }
            Button(
                onClick = {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Done") }
        }
    }
}

// ─── Log Outfit Wizard ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogOutfitWizard(
    uiState: RateOutfitUiState,
    availableItems: List<ClothingItem>,
    viewModel: RateOutfitViewModel,
    onNavigateBack: () -> Unit,
    externalCancelRequested: Boolean = false,
    onExternalCancelConfirmed: () -> Unit = {},
    onExternalCancelDismissed: () -> Unit = {}
) {
    val stepIndex = when (uiState.logStep) {
        LogOutfitStep.DATE_TIME_LOCATION -> 0
        LogOutfitStep.OUTFIT_CATEGORIES  -> 1
        LogOutfitStep.SUMMARY            -> 2
        LogOutfitStep.RATING             -> 3
    }

    val coroutineScope = rememberCoroutineScope()

    BackHandler { viewModel.handleWizardBack() }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    LaunchedEffect(externalCancelRequested) {
        if (externalCancelRequested) viewModel.showDismissDialog()
    }

    if (uiState.showDismissDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.hideDismissDialog()
                if (externalCancelRequested) onExternalCancelDismissed()
            },
            title = { Text("Discard outfit?") },
            text = { Text("All entered data will be lost. Are you sure?") },
            confirmButton = {
                TextButton(onClick = {
                    // Hide dialog first, then wait for its exit animation (~300 ms) to finish
                    // before triggering the screen transition so the dialog is fully gone.
                    viewModel.hideDismissDialog()
                    coroutineScope.launch {
                        delay(300)
                        if (externalCancelRequested) onExternalCancelConfirmed()
                        else onNavigateBack()
                    }
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.hideDismissDialog()
                    if (externalCancelRequested) onExternalCancelDismissed()
                }) { Text("Keep editing") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Outfit", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = viewModel::handleWizardBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::showDismissDialog) { Text("Cancel") }
                },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        AnimatedContent(
            targetState = stepIndex,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it } + fadeIn() togetherWith
                        slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith
                        slideOutHorizontally { it } + fadeOut()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            label = "wizard_step"
        ) { index ->
            when (index) {
                0    -> WizardDateTimeLocationStep(uiState, viewModel)
                1    -> WizardAllCategoriesStep(uiState, availableItems, viewModel)
                2    -> WizardSummaryStep(uiState, availableItems, viewModel)
                else -> WizardRatingStep(uiState, viewModel)
            }
        }
    }
}

// ─── Step 1: Location / Date / Time / Weather ────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WizardDateTimeLocationStep(
    uiState: RateOutfitUiState,
    viewModel: RateOutfitViewModel
) {
    val context = LocalContext.current
    val today = LocalDate.now()

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 88.dp)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("When and where did you work out?", style = MaterialTheme.typography.titleLarge)

            // Location \u2014 the current place by default; tapping opens the location sheet.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Location", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = viewModel::openLocationSheet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (uiState.chosenLocation == null) Icons.Default.MyLocation else Icons.Default.Place,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(uiState.logLocationName.ifBlank { Place.DEFAULT_NAME })
                }
                if (uiState.isLoadingLocationWeather) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp))
                        Text("Loading weather\u2026", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Date picker
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Date", style = MaterialTheme.typography.labelLarge)
                val dateSelected = uiState.logLocationSelected && !uiState.isLoadingLocationWeather
                val showDatePicker = {
                    val maxMs = System.currentTimeMillis()
                    val dialog = DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            viewModel.selectDate(LocalDate.of(year, month + 1, day))
                        },
                        uiState.selectedDate.year,
                        uiState.selectedDate.monthValue - 1,
                        uiState.selectedDate.dayOfMonth
                    )
                    dialog.datePicker.maxDate = maxMs
                    dialog.show()
                }
                OutlinedButton(
                    onClick = { showDatePicker() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = dateSelected
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        when {
                            !dateSelected -> "Pick a city first"
                            uiState.selectedDate == today -> "Today"
                            uiState.selectedDate == today.minusDays(1) -> "Yesterday"
                            else -> uiState.selectedDate.format(dateFormatter)
                        }
                    )
                }
            }

            // Time picker — shown once date weather is loaded
            val selectedHour = uiState.selectedHour
            when {
                uiState.isLoadingDateWeather -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp))
                        Text("Loading weather\u2026", style = MaterialTheme.typography.bodySmall)
                    }
                }
                uiState.logLocationSelected && !uiState.isLoadingLocationWeather -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Time", style = MaterialTheme.typography.labelLarge)
                        OutlinedButton(
                            onClick = {
                                val defaultHour = selectedHour?.time?.hour ?: LocalTime.now().hour
                                TimePickerDialog(
                                    context,
                                    { _, hour, _ -> viewModel.selectHourByClockHour(hour) },
                                    defaultHour,
                                    0,
                                    true
                                ).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                selectedHour?.time?.format(timeFormatter) ?: "Pick time"
                            )
                        }
                    }
                }
            }

            // Weather info — only shown once both date and time are selected.
            if (selectedHour != null && !uiState.isLoadingLocationWeather) {
                val dateLabel = when (uiState.selectedDate) {
                    today              -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else               -> uiState.selectedDate.format(dateFormatter)
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Weather \u2013 $dateLabel at ${selectedHour.time.format(timeFormatter)}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    WeatherInfoRow(stringResource(R.string.temperature_real), "${roundedTemp(selectedHour.temperatureCelsius)}\u00B0C")
                    WeatherInfoRow(stringResource(R.string.temperature_apparent), "${roundedTemp(selectedHour.apparentTemperatureCelsius)}\u00B0C")
                    WeatherInfoRow("Wind", "${selectedHour.windSpeedKmh.toInt()} km/h ${selectedHour.windDirectionLabel}")
                    WeatherInfoRow("UV index", "${selectedHour.uvIndex}")
                    WeatherInfoRow("Cloud cover", "${selectedHour.cloudCoverPercent}%")
                    WeatherInfoRow("Rain probability", "${selectedHour.precipitationProbabilityPercent}%")
                }
            }
        }

        // Sticky bottom button
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Button(
                onClick = viewModel::advanceToCategories,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = !uiState.isLoadingLocationWeather && !uiState.isLoadingDateWeather && uiState.selectedHour != null
            ) { Text("Next") }
        }
    }

    if (uiState.showLocationSheet) LocationSheet(uiState, viewModel)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LocationSheet(uiState: RateOutfitUiState, viewModel: RateOutfitViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searching by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = viewModel::dismissLocationSheet, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Where did you work out?", style = MaterialTheme.typography.titleLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = uiState.chosenLocation == null,
                    onClick = { viewModel.selectLocation(null) },
                    label = { Text(Place.DEFAULT_NAME) },
                    leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, Modifier.size(18.dp)) }
                )
                uiState.recentPlaces.forEach { place ->
                    FilterChip(
                        selected = uiState.chosenLocation == place.location,
                        onClick = { viewModel.selectLocation(place.location) },
                        label = {
                            Text(place.distanceLabel?.let { "${place.location.name} \u00B7 $it" } ?: place.location.name)
                        }
                    )
                }
                FilterChip(
                    selected = searching,
                    onClick = { searching = !searching },
                    label = { Text("Search city\u2026") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, Modifier.size(18.dp)) }
                )
            }
            if (searching) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                OutlinedTextField(
                    value = uiState.locationQuery,
                    onValueChange = viewModel::setLocationQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("City") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                if (uiState.searchFailed) {
                    Text(
                        "Couldn\u2019t search \u2014 check your connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Column {
                    uiState.searchResults.forEach { result ->
                        ListItem(
                            headlineContent = { Text(result.location.name) },
                            supportingContent = result.region.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                            leadingContent = { Icon(Icons.Default.Place, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { viewModel.selectLocation(result.location) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ─── Chip colors ──────────────────────────────────────────────────────────────
//   • Inactive (deselected): transparent bg → onSurface label (matches header)
//   • Active   (selected):   primaryContainer → onPrimaryContainer label

private data class ChipColorSet(
    val inactiveBg: Color,
    val inactiveLabel: Color,
    val activeBg: Color,
    val activeLabel: Color
)

@Composable
private fun outfitChipColorSet(): ChipColorSet = ChipColorSet(
    inactiveBg    = Color.Transparent,
    inactiveLabel = MaterialTheme.colorScheme.onSurface,
    activeBg      = MaterialTheme.colorScheme.primaryContainer,
    activeLabel   = MaterialTheme.colorScheme.onPrimaryContainer
)

// ─── Step 2: All outfit categories on one scrollable screen ──────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardAllCategoriesStep(
    uiState: RateOutfitUiState,
    availableItems: List<ClothingItem>,
    viewModel: RateOutfitViewModel
) {
    val hasLikely = uiState.likelyItemIds.isNotEmpty()
    val likelyItems   = if (hasLikely) availableItems.filter { it.id in uiState.likelyItemIds }.sortedBy { it.name }   else emptyList()
    val unlikelyItems = if (hasLikely) availableItems.filter { it.id !in uiState.likelyItemIds }.sortedBy { it.name } else availableItems.sortedBy { it.name }

    val selectedCount = uiState.selectedItemIds.size
    val selectedLabel = if (selectedCount == 1) "1 item selected" else "$selectedCount items selected"

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 16.dp)
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(selectedLabel, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (hasLikely) {
            Text("Likely Items", style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ))
            OutfitItemChipGroup(likelyItems, uiState.selectedItemIds, viewModel)
            Text("Unlikely Items", style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            ))
        }
        OutfitItemChipGroup(unlikelyItems, uiState.selectedItemIds, viewModel)

        // Navigation at the bottom of the scrollable content
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::handleWizardBack,
                modifier = Modifier.weight(1f)
            ) { Text("Previous") }
            Button(
                onClick = viewModel::advanceToSummary,
                modifier = Modifier.weight(1f)
            ) { Text("Next") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutfitItemChipGroup(
    items: List<ClothingItem>,
    selectedItemIds: Set<Long>,
    viewModel: RateOutfitViewModel
) {
    val colors = outfitChipColorSet()
    val chipBorder = FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = false,
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = Color.Transparent
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            val isSelected = item.id in selectedItemIds
            val chipBg    = if (isSelected) colors.activeBg    else colors.inactiveBg
            val chipLabel = if (isSelected) colors.activeLabel else colors.inactiveLabel
            FilterChip(
                selected = isSelected,
                onClick = { viewModel.toggleItem(item.id) },
                label = { Text(item.name, style = MaterialTheme.typography.bodySmall) },
                border = chipBorder,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = chipBg,
                    selectedContainerColor = chipBg,
                    labelColor = chipLabel,
                    selectedLabelColor = chipLabel,
                )
            )
        }
    }
}

// ─── Step 4: Summary ─────────────────────────────────────────────────────────

@Composable
private fun WizardSummaryStep(
    uiState: RateOutfitUiState,
    availableItems: List<ClothingItem>,
    viewModel: RateOutfitViewModel
) {
    val hour = uiState.selectedHour
    val selectedItems = availableItems
        .filter { it.id in uiState.selectedItemIds }
        .inHeadToToeOrder()

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 88.dp)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Summary", style = MaterialTheme.typography.headlineMedium)

            val today = LocalDate.now()
            SummarySection("Date and Time") {
                val dateLabel = when (uiState.selectedDate) {
                    today              -> "Today"
                    today.minusDays(1) -> "Yesterday"
                    else               -> uiState.selectedDate.format(dateFormatter)
                }
                Text(
                    text = "$dateLabel at ${hour?.time?.format(timeFormatter) ?: "\u2014"}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            SummarySection("Location") {
                Text(uiState.logLocationName, style = MaterialTheme.typography.bodyLarge)
            }

            if (hour != null) {
                SummarySection("Weather") {
                    Text(
                        text = "${roundedTemp(hour.temperatureCelsius)}\u00B0C, " +
                            "${stringResource(R.string.temperature_apparent).lowercase()} " +
                            "${roundedTemp(hour.apparentTemperatureCelsius)}\u00B0C",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Wind ${hour.windSpeedKmh.toInt()} km/h \u00B7 " +
                            "UV ${hour.uvIndex} \u00B7 " +
                            "Rain ${hour.precipitationProbabilityPercent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            SummarySection("Outfit") {
                if (selectedItems.isEmpty()) {
                    Text(
                        "No items selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = selectedItems.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

        }

        // Sticky bottom buttons
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::goBackFromSummary,
                    modifier = Modifier.weight(1f)
                ) { Text("Previous") }
                Button(
                    onClick = if (uiState.mode is OutfitScreenMode.NewLive) viewModel::save else viewModel::advanceToRating,
                    modifier = Modifier.weight(1f),
                    enabled = selectedItems.isNotEmpty()
                ) { Text(if (uiState.mode is OutfitScreenMode.NewLive) "Finish" else "Next") }
            }
        }
    }
}

// ─── Step 5: Rating ───────────────────────────────────────────────────────────

@Composable
private fun WizardRatingStep(
    uiState: RateOutfitUiState,
    viewModel: RateOutfitViewModel
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 88.dp)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Rate your outfit", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Outfit Notes") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How did the outfit feel?", style = MaterialTheme.typography.titleSmall)
                ComfortRatingBar(
                    selectedRating = uiState.comfortRating,
                    onRatingSelected = viewModel::setRating
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Button(
                onClick = viewModel::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) { Text("Save") }
        }
    }
}

@Composable
private fun SummarySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        content()
    }
}
