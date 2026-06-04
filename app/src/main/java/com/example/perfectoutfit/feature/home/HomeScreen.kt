package com.example.perfectoutfit.feature.home

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.BODY_PART_DISPLAY_ORDER
import com.example.perfectoutfit.core.model.FavoriteLocation
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.ui.components.ClothingItemChip
import com.example.perfectoutfit.ui.components.LocationDropdown
import com.example.perfectoutfit.ui.components.ratingLabel
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private enum class WizardStep { LOCATION_SPORT, RESULT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddLocation: () -> Unit,
    onNavigateToNewOutfit: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var wizardStep by remember { mutableStateOf(WizardStep.LOCATION_SPORT) }
    var outfitAccepted by remember { mutableStateOf(false) }
    var pendingSport by remember { mutableStateOf<Pair<Sport, Int>?>(null) }

    BackHandler(enabled = wizardStep == WizardStep.RESULT) {
        wizardStep = WizardStep.LOCATION_SPORT
    }

    LaunchedEffect(wizardStep) {
        if (wizardStep == WizardStep.RESULT) {
            outfitAccepted = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshWeather()
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error?.contains("permission", ignoreCase = true) == true) {
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onLocationPermissionMaybeGranted()
    }

    val currentCity = uiState.currentCityName
    if (pendingSport != null && currentCity != null
        && !uiState.selectedLocationName.startsWith("Current location")
        && !uiState.selectedLocationName.equals(currentCity, ignoreCase = true)) {
        AlertDialog(
            onDismissRequest = { pendingSport = null },
            title = { Text("Location mismatch") },
            text = {
                Text(
                    "Chosen location (${uiState.selectedLocationName}) and current location " +
                    "($currentCity) do not match. Do you want to continue anyways?"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val (sport, hours) = pendingSport!!
                    pendingSport = null
                    viewModel.selectSport(sport, hours)
                    wizardStep = WizardStep.RESULT
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { pendingSport = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            when (wizardStep) {
                WizardStep.LOCATION_SPORT -> TopAppBar(
                    title = { Text("Outfit Recommendation") },
                    windowInsets = WindowInsets(0)
                )
                WizardStep.RESULT -> TopAppBar(
                    title = { Text("Recommendation") },
                    navigationIcon = {
                        IconButton(onClick = { wizardStep = WizardStep.LOCATION_SPORT }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    windowInsets = WindowInsets(0)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (wizardStep) {
                WizardStep.LOCATION_SPORT -> LocationSportStep(
                    selectedLocationName = uiState.selectedLocationName,
                    favoriteLocations = uiState.favoriteLocations,
                    onCurrentLocationSelected = viewModel::selectCurrentLocation,
                    onFavoriteSelected = viewModel::selectFavoriteLocation,
                    onAddLocation = onAddLocation,
                    onSportSelected = { sport, hours ->
                        if (!uiState.selectedLocationName.startsWith("Current location")
                            && uiState.currentCityName != null
                            && !uiState.selectedLocationName.equals(uiState.currentCityName, ignoreCase = true)) {
                            pendingSport = sport to hours
                        } else {
                            viewModel.selectSport(sport, hours)
                            wizardStep = WizardStep.RESULT
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                WizardStep.RESULT -> ResultStep(
                    uiState = uiState,
                    outfitAccepted = outfitAccepted,
                    onAcceptRecommendation = { rec ->
                        viewModel.acceptRecommendation(rec)
                        outfitAccepted = true
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Outfit saved! \u2713")
                        }
                    },
                    onEditOutfit = {
                        viewModel.prepareEditOutfit()
                        onNavigateToNewOutfit()
                    },
                    onCustomOutfit = {
                        viewModel.prepareCustomOutfit()
                        onNavigateToNewOutfit()
                    },
                    onRefresh = viewModel::refreshWeather,
                    onWorkoutTabSelected = viewModel::selectWorkoutTab,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LocationSportStep(
    selectedLocationName: String,
    favoriteLocations: List<FavoriteLocation>,
    onCurrentLocationSelected: () -> Unit,
    onFavoriteSelected: (FavoriteLocation) -> Unit,
    onAddLocation: () -> Unit,
    onSportSelected: (Sport, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isLongWorkout by remember { mutableStateOf(false) }
    var workoutHours by remember { mutableIntStateOf(2) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        LocationDropdown(
            selectedLocationName = selectedLocationName,
            favoriteLocations = favoriteLocations,
            onCurrentLocationSelected = onCurrentLocationSelected,
            onFavoriteSelected = onFavoriteSelected,
            onAddLocation = onAddLocation
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Long workout", style = MaterialTheme.typography.bodyLarge)
            Switch(checked = isLongWorkout, onCheckedChange = { isLongWorkout = it })
            if (isLongWorkout) {
                Spacer(modifier = Modifier.weight(1f))
                OutlinedIconButton(
                    onClick = { if (workoutHours > 2) workoutHours-- },
                    enabled = workoutHours > 2
                ) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                Text(
                    "${workoutHours}h",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.Center
                )
                OutlinedIconButton(
                    onClick = { if (workoutHours < 23) workoutHours++ },
                    enabled = workoutHours < 23
                ) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SportTile(
                iconRes = R.drawable.ic_bike,
                label = "Cycling",
                onClick = { onSportSelected(Sport.CYCLING, if (isLongWorkout) workoutHours else 1) },
                modifier = Modifier.weight(1f)
            )
            SportTile(
                iconRes = R.drawable.ic_sprint,
                label = "Running",
                onClick = { onSportSelected(Sport.RUNNING, if (isLongWorkout) workoutHours else 1) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SportTile(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultStep(
    uiState: HomeUiState,
    outfitAccepted: Boolean,
    onAcceptRecommendation: (OutfitEntryWithDetails) -> Unit,
    onEditOutfit: () -> Unit,
    onCustomOutfit: () -> Unit,
    onRefresh: () -> Unit,
    onWorkoutTabSelected: (WorkoutTab) -> Unit,
    modifier: Modifier = Modifier
) {
    PullToRefreshBox(
        isRefreshing = uiState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 80.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ResultSummaryBar(
                locationName = uiState.selectedLocationName,
                sport = uiState.selectedSport
            )

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            uiState.loadingMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else if (uiState.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onRefresh) { Text("Retry") }
                    }
                }
            } else {
                if (uiState.workoutDurationHours >= 2) {
                    val selectedTabIndex = if (uiState.activeWorkoutTab == WorkoutTab.COLDEST) 0 else 1
                    val hourFmt = DateTimeFormatter.ofPattern("HH:mm")
                    val coldestTime = uiState.hourlyWeather.getOrNull(uiState.coldestHourIndex)?.time?.format(hourFmt)
                    val warmestTime = uiState.hourlyWeather.getOrNull(uiState.warmestHourIndex)?.time?.format(hourFmt)
                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { onWorkoutTabSelected(WorkoutTab.COLDEST) },
                            text = { Text(if (coldestTime != null) "Coldest ($coldestTime)" else "Coldest hour") }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { onWorkoutTabSelected(WorkoutTab.WARMEST) },
                            text = { Text(if (warmestTime != null) "Warmest ($warmestTime)" else "Warmest hour") }
                        )
                    }
                }

                val currentWeather = uiState.activeHour
                if (currentWeather != null) {
                    CompactWeatherRow(
                        displayTemp = uiState.activeDisplayTemp,
                        weather = currentWeather,
                        useApparentTemperature = uiState.useApparentTemperature
                    )
                }

                val recommendation = uiState.activeRecommendation
                val showRecommendation = recommendation != null && !outfitAccepted

                if (showRecommendation && recommendation != null) {
                    WizardRecommendationCard(
                        recommendation = recommendation,
                        onAccept = { onAcceptRecommendation(recommendation) },
                        onEditOutfit = onEditOutfit
                    )
                } else if (!outfitAccepted) {
                    NoRecommendationCard(onCustomOutfit = onCustomOutfit)
                }

                if (currentWeather != null) {
                    if (currentWeather.uvIndex >= 5) {
                        UvInfoCard(uvIndex = currentWeather.uvIndex)
                    }
                    if (currentWeather.windSpeedKmh >= 20.0) {
                        WindInfoCard(
                            windSpeedKmh = currentWeather.windSpeedKmh.toInt(),
                            windDirection = currentWeather.windDirectionLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSummaryBar(
    locationName: String,
    sport: Sport
) {
    val sportIconRes = when (sport) {
        Sport.CYCLING -> R.drawable.ic_bike
        Sport.RUNNING -> R.drawable.ic_sprint
    }
    val sportName = sport.name.lowercase().replaceFirstChar { it.uppercase() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = locationName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            VerticalDivider(
                modifier = Modifier
                    .height(20.dp)
                    .padding(horizontal = 8.dp),
                color = MaterialTheme.colorScheme.outline
            )

            Icon(
                painterResource(sportIconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = sportName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CompactWeatherRow(
    displayTemp: Int,
    weather: HourlyWeather,
    useApparentTemperature: Boolean
) {
    val tempLabel = if (useApparentTemperature) "Feels like" else "Temperature"

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherMetric(
                value = "${displayTemp}°C",
                label = tempLabel,
                valueColor = MaterialTheme.colorScheme.primary
            )
            WeatherMetric(
                value = "${weather.uvIndex}",
                label = "UV",
                valueColor = uvColor(weather.uvIndex)
            )
            WeatherMetric(
                value = "${weather.windSpeedKmh.toInt()} km/h",
                label = "Wind",
                valueColor = windColor(weather.windSpeedKmh)
            )
            WeatherMetric(
                value = "${weather.precipitationProbabilityPercent}%",
                label = "Rain",
                valueColor = rainColor(weather.precipitationProbabilityPercent)
            )
        }
    }
}

@Composable
private fun WeatherMetric(value: String, label: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WizardRecommendationCard(
    recommendation: OutfitEntryWithDetails,
    onAccept: () -> Unit,
    onEditOutfit: () -> Unit
) {
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
                text = "Recommended outfit",
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onAccept, modifier = Modifier.weight(1f)) {
                    Text("Use outfit")
                }
                OutlinedButton(onClick = onEditOutfit, modifier = Modifier.weight(1f)) {
                    Text("Edit outfit")
                }
            }
        }
    }
}

@Composable
private fun NoRecommendationCard(
    onCustomOutfit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "No outfit recommendation found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No rated outfits for this temperature yet. Go out and rate your outfit when you return!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(onClick = onCustomOutfit, modifier = Modifier.fillMaxWidth()) {
                Text("Custom outfit")
            }
        }
    }
}

@Composable
private fun UvInfoCard(uvIndex: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "UV-Index",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The UV-Index is $uvIndex. Consider using sun glasses as well as UV sleeves and/or sun lotion.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun WindInfoCard(windSpeedKmh: Int, windDirection: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Wind speed",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "The wind speed is $windSpeedKmh km/h. The wind direction is $windDirection.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// ── Color helpers (UV / wind / rain indicators) ──────────────────────────────

private val ColorGreen  = Color(0xFF2E7D32)
private val ColorYellow = Color(0xFFF57F17)
private val ColorRed    = Color(0xFFC62828)

private fun uvColor(uv: Int)        = when { uv <= 2   -> ColorGreen; uv <= 5  -> ColorYellow; else -> ColorRed }
private fun windColor(kmh: Double)  = when { kmh < 10  -> ColorGreen; kmh <= 20 -> ColorYellow; else -> ColorRed }
private fun rainColor(pct: Int)     = when { pct < 20  -> ColorGreen; pct < 50 -> ColorYellow; else -> ColorRed }
