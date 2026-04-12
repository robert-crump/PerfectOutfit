package com.example.perfectoutfit.feature.rate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.ui.components.ComfortRatingBar
import com.example.perfectoutfit.ui.components.verticalScrollbar
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (uiState.isNewOutfitMode) "Log Outfit" else "Rate Outfit",
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

    if (uiState.isNewOutfitMode) {
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
            highlightRating = highlightRating
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
    highlightRating: Boolean = false
) {
    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .imePadding()
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            uiState.weatherSnapshot?.let { ws ->
                if (ws.locationName.isNotEmpty()) {
                    Text(
                        text = ws.locationName,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    text = "Weather: ${ws.temperatureCelsius.toInt()}\u00B0C " +
                        "(feels like ${ws.apparentTemperatureCelsius.toInt()}\u00B0C)",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Wind: ${ws.windSpeedKmh.toInt()} km/h | " +
                        "UV: ${ws.uvIndex} | " +
                        "Cloud: ${ws.cloudCoverPercent}%",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text("Select items you wore:", style = MaterialTheme.typography.titleMedium)

            BodyPart.entries.forEach { bodyPart ->
                val bodyPartItems = availableItems.filter { it.bodyPart == bodyPart }
                if (bodyPartItems.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(bodyPart.displayName, style = MaterialTheme.typography.titleSmall)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            bodyPartItems.forEach { item ->
                                FilterChip(
                                    selected = item.id in uiState.selectedItemIds,
                                    onClick = { viewModel.toggleItem(item.id) },
                                    label = { Text(item.name, style = MaterialTheme.typography.bodySmall) }
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Outfit Notes") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = animatedAlpha))
                    .padding(vertical = 4.dp)
            ) {
                ComfortRatingBar(
                    selectedRating = uiState.comfortRating,
                    onRatingSelected = viewModel::setRating
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
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

            // Location picker
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Location", style = MaterialTheme.typography.labelLarge)
                if (uiState.isLoadingLocationWeather) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp))
                        Text("Loading weather\u2026", style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = if (uiState.logLocationSelected) uiState.logLocationName else "",
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select a city") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                            }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Current Location") },
                                onClick = {
                                    viewModel.selectLogLocation(null)
                                    expanded = false
                                }
                            )
                            uiState.logFavLocations.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc.name) },
                                    onClick = {
                                        viewModel.selectLogLocation(loc)
                                        expanded = false
                                    }
                                )
                            }
                        }
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
                    WeatherInfoRow("Temperature", "${selectedHour.temperatureCelsius.toInt()}\u00B0C")
                    WeatherInfoRow("Apparent temp.", "${selectedHour.apparentTemperatureCelsius.toInt()}\u00B0C")
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

// ─── Chip colors (wallpaper-derived, single color set) ───────────────────────
//   • Inactive (deselected): transparent bg → onSurface label (matches header)
//   • Active   (selected):   accent1 tone 100 (light) → tone 900 label (dark)
// Falls back to static indigo tones on pre-Android 12 devices.

private data class ChipColorSet(
    val inactiveBg: Color,
    val inactiveLabel: Color,
    val activeBg: Color,
    val activeLabel: Color
)

@Composable
private fun outfitChipColorSet(): ChipColorSet {
    val inactiveLabel = MaterialTheme.colorScheme.onSurface
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        return ChipColorSet(
            inactiveBg    = Color.Transparent,
            inactiveLabel = inactiveLabel,
            activeBg      = Color(ContextCompat.getColor(context, android.R.color.system_accent1_100)),
            activeLabel   = Color(ContextCompat.getColor(context, android.R.color.system_accent1_900))
        )
    }
    // Fallback for pre-Android 12
    return ChipColorSet(
        inactiveBg    = Color.Transparent,
        inactiveLabel = inactiveLabel,
        activeBg      = Color(0xFFE8EAF6), // light indigo
        activeLabel   = Color(0xFF1A237E)
    )
}

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
        .sortedBy { item -> LOG_OUTFIT_CATEGORY_ORDER.indexOf(item.bodyPart).takeIf { it >= 0 } ?: Int.MAX_VALUE }

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
                        text = "${hour.temperatureCelsius.toInt()}\u00B0C, feels like " +
                            "${hour.apparentTemperatureCelsius.toInt()}\u00B0C",
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
                    onClick = if (uiState.isLiveMode) viewModel::save else viewModel::advanceToRating,
                    modifier = Modifier.weight(1f),
                    enabled = selectedItems.isNotEmpty()
                ) { Text(if (uiState.isLiveMode) "Finish" else "Next") }
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
            ComfortRatingBar(
                selectedRating = uiState.comfortRating,
                onRatingSelected = viewModel::setRating
            )
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
