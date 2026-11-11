package com.example.perfectoutfit.feature.rate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.Place
import com.example.perfectoutfit.core.model.formatPercent
import com.example.perfectoutfit.core.model.formatWindKmh
import com.example.perfectoutfit.core.model.loggedRainClass
import com.example.perfectoutfit.core.model.rainAmountLine
import com.example.perfectoutfit.core.model.headToToeGroups
import com.example.perfectoutfit.core.model.inHeadToToeOrder
import com.example.perfectoutfit.core.model.roundedTemp
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.outfit.LogMode
import com.example.perfectoutfit.ui.components.ComfortRatingBar
import com.example.perfectoutfit.ui.components.entryDateLabel
import com.example.perfectoutfit.ui.components.neutralCardColors
import com.example.perfectoutfit.ui.components.verticalScrollbar
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** The wizard's label for a date: Today, Yesterday, else the entry date format. */
private fun LogWizardState.dateLabel(date: LocalDate = this.date) =
    entryDateLabel(date, today, withWeekday = true, relative = true)

/** Logging a new outfit (live from Home, or after the fact) through the step-by-step wizard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogOutfitScreen(
    onNavigateBack: () -> Unit,
    externalCancelRequested: Boolean = false,
    onExternalCancelConfirmed: () -> Unit = {},
    onExternalCancelDismissed: () -> Unit = {},
    viewModel: LogOutfitViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val availableItems by viewModel.availableItems.collectAsStateWithLifecycle()
    val placeSearch by viewModel.placeSearch.collectAsStateWithLifecycle()
    val wizard = uiState.wizard

    // Nothing is entered while loading; once the wizard is up, leaving always asks.
    val requestLeave = rememberDiscardGuard(
        hasUnsavedWork = wizard != null,
        title = "Discard outfit?",
        text = "All entered data will be lost. Are you sure?",
        onLeave = onNavigateBack,
        externalCancelRequested = externalCancelRequested,
        onExternalCancelConfirmed = onExternalCancelConfirmed,
        onExternalCancelDismissed = onExternalCancelDismissed
    )
    val back = { if (viewModel.back()) requestLeave() }
    BackHandler(onBack = back)

    if (wizard == null) {
        OutfitLoadingScaffold(title = "Log Outfit", onBack = requestLeave)
        return
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.saveState) {
        if (uiState.saveState == SaveState.FAILED) {
            viewModel.onSaveFailureShown()
            snackbarHostState.showSnackbar("Couldn't save the outfit — try again")
        }
    }
    // Next (and the final save) waits while an outfit is being saved.
    val canGoNext = wizard.canGoNext && uiState.saveState != SaveState.SAVING

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log Outfit", style = MaterialTheme.typography.headlineMedium) },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = requestLeave) { Text("Cancel") } },
                windowInsets = WindowInsets(0)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        AnimatedContent(
            targetState = wizard.step,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
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
        ) { step ->
            when (step) {
                LogStep.WHEN_WHERE -> WhenWhereStep(wizard, canGoNext, viewModel)
                LogStep.ITEMS -> ItemsStep(wizard, canGoNext, uiState.likelyItemIds, availableItems, viewModel, onBack = back)
                LogStep.SUMMARY -> SummaryStep(wizard, canGoNext, availableItems, viewModel, onBack = back)
                LogStep.RATING -> RatingStep(wizard, canGoNext, viewModel)
            }
        }
    }

    if (placeSearch.showSheet) PlaceSheet(wizard, placeSearch, viewModel)
}

// ─── When and where ───────────────────────────────────────────────────────────

@Composable
private fun WhenWhereStep(wizard: LogWizardState, canGoNext: Boolean, viewModel: LogOutfitViewModel) {
    val context = LocalContext.current
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

            // Location — the current place by default; tapping opens the place sheet.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Location", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(onClick = viewModel::openPlaceSheet, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (wizard.place == null) Icons.Default.MyLocation else Icons.Default.Place,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(wizard.placeName.ifBlank { Place.DEFAULT_NAME })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Date", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = {
                        val dialog = DatePickerDialog(
                            context,
                            { _, year, month, day -> viewModel.pickDate(LocalDate.of(year, month + 1, day)) },
                            wizard.date.year,
                            wizard.date.monthValue - 1,
                            wizard.date.dayOfMonth
                        )
                        // The wizard's max date, in the device zone the picker counts in.
                        dialog.datePicker.maxDate = wizard.maxPickableDate.atTime(23, 59)
                            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        dialog.show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(wizard.dateLabel())
                }
            }

            val selectedHour = wizard.selectedHour
            if (wizard.loadingHours) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp))
                    Text("Loading weather…", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Time", style = MaterialTheme.typography.labelLarge)
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, hour, _ -> viewModel.pickClockHour(hour) },
                                wizard.pickerDefaultHour,
                                0,
                                true
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedHour?.time?.format(timeFormatter) ?: "Pick time")
                    }
                }
            }

            if (selectedHour != null) HourWeather(wizard, selectedHour)
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Button(
                onClick = viewModel::next,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = canGoNext
            ) { Text("Next") }
        }
    }
}

@Composable
private fun HourWeather(wizard: LogWizardState, hour: HourlyWeather) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Weather – ${wizard.dateLabel()} at ${hour.time.format(timeFormatter)}",
            style = MaterialTheme.typography.labelLarge
        )
        WeatherInfoRow(stringResource(R.string.temperature_real), "${roundedTemp(hour.temperatureCelsius)}°C")
        WeatherInfoRow(stringResource(R.string.temperature_apparent), "${roundedTemp(hour.apparentTemperatureCelsius)}°C")
        WeatherInfoRow("Wind", "${formatWindKmh(hour.windSpeedKmh)} ${hour.windDirectionLabel}")
        WeatherInfoRow("UV index", "${hour.uvIndex}")
        WeatherInfoRow("Cloud cover", formatPercent(hour.cloudCoverPercent))
        WeatherInfoRow("Rain probability", formatPercent(hour.precipitationProbabilityPercent))
        WeatherInfoRow(
            "Precipitation",
            hour.precipitationMm?.let { rainAmountLine(it, hour.loggedRainClass()) } ?: "—"
        )
    }
}

@Composable
private fun WeatherInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PlaceSheet(wizard: LogWizardState, search: PlaceSearchState, viewModel: LogOutfitViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searching by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = viewModel::closePlaceSheet, sheetState = sheetState) {
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
                    selected = wizard.place == null,
                    onClick = { viewModel.pickPlace(null) },
                    label = { Text(Place.DEFAULT_NAME) },
                    leadingIcon = { Icon(Icons.Default.MyLocation, contentDescription = null, Modifier.size(18.dp)) }
                )
                search.recentPlaces.forEach { place ->
                    FilterChip(
                        selected = wizard.place == place.location,
                        onClick = { viewModel.pickPlace(place.location) },
                        label = {
                            Text(place.distanceLabel?.let { "${place.location.name} · $it" } ?: place.location.name)
                        }
                    )
                }
                FilterChip(
                    selected = searching,
                    onClick = { searching = !searching },
                    label = { Text("Search city…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, Modifier.size(18.dp)) }
                )
            }
            if (searching) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.requestFocus() }
                OutlinedTextField(
                    value = search.query,
                    onValueChange = viewModel::setPlaceQuery,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    placeholder = { Text("City") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
                if (search.failed) {
                    Text(
                        "Couldn’t search — check your connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Column {
                    search.results.forEach { result ->
                        ListItem(
                            headlineContent = { Text(result.location.name) },
                            supportingContent = result.region.takeIf { it.isNotBlank() }?.let { { Text(it) } },
                            leadingContent = { Icon(Icons.Default.Place, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { viewModel.pickPlace(result.location) }
                        )
                    }
                }
            }
        }
    }
}

// ─── Items ────────────────────────────────────────────────────────────────────

@Composable
private fun ItemsStep(
    wizard: LogWizardState,
    canGoNext: Boolean,
    likelyItemIds: Set<Long>,
    availableItems: List<ClothingItem>,
    viewModel: LogOutfitViewModel,
    onBack: () -> Unit
) {
    val hasLikely = likelyItemIds.isNotEmpty()
    val (likelyItems, otherItems) = availableItems.inHeadToToeOrder().partition { hasLikely && it.id in likelyItemIds }

    val selectedCount = wizard.itemIds.size
    val selectedLabel = if (selectedCount == 1) "1 item selected" else "$selectedCount items selected"

    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(selectedLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // Without likely items there is nothing to split: one card holds them all.
            if (hasLikely) {
                ItemsCard("Likely Items", likelyItems, wizard.itemIds, viewModel::toggleItem)
                ItemsCard("Unlikely Items", otherItems, wizard.itemIds, viewModel::toggleItem)
            } else {
                ItemsCard("All Items", otherItems, wizard.itemIds, viewModel::toggleItem)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            if (wizard.mode == LogMode.LIVE) {
                // A live log is only this step: the outfit is used right away.
                Button(
                    onClick = viewModel::next,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = canGoNext
                ) { Text(finishLabel(wizard)) }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Previous") }
                    Button(onClick = viewModel::next, modifier = Modifier.weight(1f), enabled = canGoNext) {
                        Text("Next")
                    }
                }
            }
        }
    }
}

/** A titled card of [items] chips, grouped by body part; nothing at all when [items] is empty. */
@Composable
private fun ItemsCard(
    title: String,
    items: List<ClothingItem>,
    selectedItemIds: Set<Long>,
    onToggle: (Long) -> Unit
) {
    if (items.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth(), colors = neutralCardColors()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            BodyPartChipGroups(items, selectedItemIds, onToggle)
        }
    }
}

/** Chips grouped under a body-part label, head to toe; items arrive already in display order. */
@Composable
private fun BodyPartChipGroups(items: List<ClothingItem>, selectedItemIds: Set<Long>, onToggle: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.headToToeGroups { it.bodyPart }.forEach { (bodyPart, group) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    bodyPart.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ItemChipGroup(group, selectedItemIds, onToggle)
            }
        }
    }
}

// ─── Summary ──────────────────────────────────────────────────────────────────

@Composable
private fun SummaryStep(
    wizard: LogWizardState,
    canGoNext: Boolean,
    availableItems: List<ClothingItem>,
    viewModel: LogOutfitViewModel,
    onBack: () -> Unit
) {
    val hour = wizard.selectedHour
    val selectedItems = availableItems.filter { it.id in wizard.itemIds }.inHeadToToeOrder()

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

            SummarySection("Date and Time") {
                Text(
                    text = "${wizard.dateLabel()} at ${hour?.time?.format(timeFormatter) ?: "—"}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            SummarySection("Location") {
                Text(wizard.placeName.ifBlank { Place.DEFAULT_NAME }, style = MaterialTheme.typography.bodyLarge)
            }

            if (hour != null) {
                SummarySection("Weather") {
                    Text(
                        text = "${roundedTemp(hour.temperatureCelsius)}°C, " +
                            "${stringResource(R.string.temperature_apparent).lowercase()} " +
                            "${roundedTemp(hour.apparentTemperatureCelsius)}°C",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Wind ${formatWindKmh(hour.windSpeedKmh)} · " +
                            "UV ${hour.uvIndex} · " +
                            "Rain ${formatPercent(hour.precipitationProbabilityPercent)} · " +
                            rainAmountLine(hour.precipitationMm, hour.loggedRainClass()),
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
                    Text(text = selectedItems.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Previous") }
                Button(onClick = viewModel::next, modifier = Modifier.weight(1f), enabled = canGoNext) {
                    Text(if (wizard.isLastStep) finishLabel(wizard) else "Next")
                }
            }
        }
    }
}

/** The last step's button: a live log uses the outfit, a past log saves. */
private fun finishLabel(wizard: LogWizardState) = if (wizard.mode == LogMode.LIVE) "Use outfit" else "Save"

// ─── Rating ───────────────────────────────────────────────────────────────────

@Composable
private fun RatingStep(wizard: LogWizardState, canGoNext: Boolean, viewModel: LogOutfitViewModel) {
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
                value = wizard.notes,
                onValueChange = viewModel::setNotes,
                label = { Text("Outfit Notes") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 2
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("How did the outfit feel?", style = MaterialTheme.typography.titleSmall)
                ComfortRatingBar(selectedRating = wizard.rating, onRatingSelected = viewModel::setRating)
            }
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shadowElevation = 4.dp
        ) {
            Button(
                onClick = viewModel::next,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = canGoNext
            ) { Text(finishLabel(wizard)) }
        }
    }
}

@Composable
private fun SummarySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
