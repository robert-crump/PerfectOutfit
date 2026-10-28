package com.example.perfectoutfit.feature.home

import android.Manifest
import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.ui.components.OutfitCard
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class WizardStep { LOCATION_ACTIVITY_TYPE, RESULT }

/**
 * A request from outside Home (the widget) to show it: [activityType]'s result step for a
 * one-hour workout, or the first step when null. Not a data class, so two taps are two requests.
 */
class HomeLaunch(val activityType: ActivityType?)

/** The pending [HomeLaunch]; Home reads it as state and clears it once shown. */
@Stable
class HomeLaunchState {
    var pending: HomeLaunch? by mutableStateOf(null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNewOutfit: () -> Unit,
    onNavigateToExplorer: (Int?) -> Unit,
    /** Bumped each time the Home tab is tapped while Home is showing. */
    tabReselections: Int = 0,
    /** Its pending launch is shown once, then cleared. */
    launchState: HomeLaunchState? = null,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var wizardStep by remember { mutableStateOf(WizardStep.LOCATION_ACTIVITY_TYPE) }

    BackHandler(enabled = wizardStep == WizardStep.RESULT) {
        wizardStep = WizardStep.LOCATION_ACTIVITY_TYPE
    }

    LaunchedEffect(tabReselections) {
        wizardStep = WizardStep.LOCATION_ACTIVITY_TYPE
    }

    // Declared after the reselection effect, so on a fresh Home the launch's step wins.
    val launch = launchState?.pending
    LaunchedEffect(launch) {
        if (launch == null) return@LaunchedEffect
        val activityType = launch.activityType
        if (activityType != null) {
            viewModel.selectActivityType(activityType, workoutDurationHours = 1)
            wizardStep = WizardStep.RESULT
        } else {
            wizardStep = WizardStep.LOCATION_ACTIVITY_TYPE
        }
        launchState.pending = null
    }

    // A saved outfit ends the flow on the activity grid; a failed save stays on the result.
    LaunchedEffect(uiState.acceptState) {
        val message = when (uiState.acceptState) {
            AcceptState.SAVED -> {
                wizardStep = WizardStep.LOCATION_ACTIVITY_TYPE
                "Outfit saved ✓"
            }
            AcceptState.FAILED -> "Couldn't save the outfit — try again"
            else -> return@LaunchedEffect
        }
        viewModel.onAcceptHandled()
        // Outside this effect, which restarts as soon as the state goes back to IDLE.
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }

    // Tracks whether we've already prompted for the location permission at least once, so a
    // recurring permission error can be told apart from a first-ever ask: shouldShowRequestPermissionRationale
    // returns false both before the first request AND after "don't ask again", so we need this
    // flag to distinguish the two and decide between showing "Retry" and "Open Settings".
    var permissionRequestedOnce by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.refreshWeather()
    }

    LaunchedEffect(uiState.error) {
        if (uiState.error?.contains("permission", ignoreCase = true) == true) {
            permissionRequestedOnce = true
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onLocationPermissionMaybeGranted()
    }

    val isPermissionError = uiState.error?.contains("permission", ignoreCase = true) == true
    val activity = context.findActivity()
    val showOpenSettings = isPermissionError && permissionRequestedOnce && activity != null &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)

    val forecastTemp = if (uiState.hourlyWeather.isNotEmpty()) uiState.activeDisplayTemp else null

    Scaffold(
        topBar = {
            when (wizardStep) {
                WizardStep.LOCATION_ACTIVITY_TYPE -> TopAppBar(
                    title = { Text("Outfit Recommendation") },
                    actions = {
                        IconButton(onClick = { onNavigateToExplorer(forecastTemp) }) {
                            Icon(Icons.Default.Info, contentDescription = "Outfit Explorer")
                        }
                    },
                    windowInsets = WindowInsets(0)
                )
                WizardStep.RESULT -> TopAppBar(
                    title = { Text(resultTitle(uiState.selectedActivityType, uiState.workoutDurationHours)) },
                    navigationIcon = {
                        IconButton(onClick = { wizardStep = WizardStep.LOCATION_ACTIVITY_TYPE }) {
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
        bottomBar = {
            if (wizardStep == WizardStep.RESULT && !uiState.isLoading && uiState.error == null) {
                val recommendation = uiState.activeRecommendation
                ResultActionBar(
                    label = if (recommendation != null) "Use outfit" else "Choose outfit",
                    enabled = uiState.acceptState != AcceptState.SAVING,
                    onClick = {
                        if (recommendation != null) {
                            viewModel.acceptRecommendation(recommendation)
                        } else {
                            viewModel.prepareCustomOutfit()
                            onNavigateToNewOutfit()
                        }
                    }
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
                WizardStep.LOCATION_ACTIVITY_TYPE -> LocationActivityStep(
                    selectedLocationName = uiState.selectedLocationName,
                    onActivityTypeSelected = { activityType, hours ->
                        viewModel.selectActivityType(activityType, hours)
                        wizardStep = WizardStep.RESULT
                    },
                    modifier = Modifier.weight(1f)
                )
                WizardStep.RESULT -> ResultStep(
                    uiState = uiState,
                    onEditOutfit = {
                        viewModel.prepareEditOutfit()
                        onNavigateToNewOutfit()
                    },
                    onRefresh = viewModel::refreshWeather,
                    onWorkoutTabSelected = viewModel::selectWorkoutTab,
                    showOpenSettings = showOpenSettings,
                    onOpenSettings = { context.openAppSettings() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** The result step's title: the activity type, plus the duration of a long workout. */
private fun resultTitle(activityType: ActivityType, workoutDurationHours: Int): String =
    if (workoutDurationHours >= 2) "${activityType.displayName} · $workoutDurationHours h"
    else activityType.displayName

@Composable
private fun LocationActivityStep(
    selectedLocationName: String,
    onActivityTypeSelected: (ActivityType, Int) -> Unit,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(selectedLocationName, style = MaterialTheme.typography.bodyLarge)
        }

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

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            ActivityType.entries.chunked(2).forEach { rowActivityTypes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowActivityTypes.forEach { activityType ->
                        ActivityTypeTile(
                            iconRes = activityType.iconRes,
                            label = activityType.displayName,
                            onClick = { onActivityTypeSelected(activityType, if (isLongWorkout) workoutHours else 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // A lone tile in the last row keeps half the width.
                    if (rowActivityTypes.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ActivityTypeTile(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(140.dp),
        colors = neutralCardColors()
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

/** Every card on Home: one neutral container, so colour is left to the actions. */
@Composable
private fun neutralCardColors() =
    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultStep(
    uiState: HomeUiState,
    onEditOutfit: () -> Unit,
    onRefresh: () -> Unit,
    onWorkoutTabSelected: (WorkoutTab) -> Unit,
    showOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
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
                // Inside the scroll, so the bottom space scrolls with the cards instead of clipping them.
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
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
                            text = uiState.error,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (showOpenSettings) {
                            Button(onClick = onOpenSettings) { Text("Open Settings") }
                        } else {
                            Button(onClick = onRefresh) { Text("Retry") }
                        }
                    }
                }
            } else {
                if (uiState.workoutDurationHours >= 2) {
                    WorkoutHourSelector(uiState = uiState, onWorkoutTabSelected = onWorkoutTabSelected)
                }

                val currentWeather = uiState.activeHour
                if (currentWeather != null) {
                    WeatherCard(
                        displayTemp = uiState.activeDisplayTemp,
                        weather = currentWeather,
                        useApparentTemperature = uiState.useApparentTemperature,
                        locationName = uiState.selectedLocationName
                    )
                }

                val recommendation = uiState.activeRecommendation
                if (recommendation != null) {
                    OutfitCard(
                        title = "Recommended outfit",
                        outfit = recommendation,
                        onEditOutfit = onEditOutfit
                    )
                } else {
                    NoRecommendationCard(
                        displayTemp = uiState.activeDisplayTemp,
                        useApparentTemperature = uiState.useApparentTemperature
                    )
                }

                // Judged from now, not the workout's hour: it's about what to pack, not what to wear.
                WeatherThresholds.rainAhead(uiState.hourlyWeather)?.let { RainAheadCard(it) }

                if (currentWeather != null && WeatherThresholds.isUvHigh(currentWeather.uvIndex)) {
                    UvInfoCard(uvIndex = currentWeather.uvIndex)
                }
            }
        }
    }
}

/** The pinned, full-width action of the result step. */
@Composable
private fun ResultActionBar(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Coldest or warmest hour of a long workout, each with its time and reference temperature. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkoutHourSelector(
    uiState: HomeUiState,
    onWorkoutTabSelected: (WorkoutTab) -> Unit
) {
    val hourFmt = DateTimeFormatter.ofPattern("HH:mm")
    val options = listOf(
        Triple(WorkoutTab.COLDEST, "Coldest", uiState.coldestHourIndex),
        Triple(WorkoutTab.WARMEST, "Warmest", uiState.warmestHourIndex)
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (tab, name, hourIndex) ->
            val time = uiState.hourlyWeather.getOrNull(hourIndex)?.time?.format(hourFmt)
            val temp = uiState.displayTempAt(hourIndex)
            val label = if (time != null && temp != null) "$name · $time · $temp°" else name
            SegmentedButton(
                selected = uiState.activeWorkoutTab == tab,
                onClick = { onWorkoutTabSelected(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WeatherCard(
    displayTemp: Int,
    weather: HourlyWeather,
    useApparentTemperature: Boolean,
    locationName: String
) {
    val tempLabel = stringResource(
        if (useApparentTemperature) R.string.temperature_apparent else R.string.temperature_real
    )
    // The value not used for matching, shown smaller so a surprising number explains itself
    val otherLabel = stringResource(
        if (useApparentTemperature) R.string.temperature_real else R.string.temperature_apparent
    )
    val otherTemp = (
        if (useApparentTemperature) weather.temperatureCelsius else weather.apparentTemperatureCelsius
    ).roundToInt()

    Card(modifier = Modifier.fillMaxWidth(), colors = neutralCardColors()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${displayTemp}°C",
                    style = MaterialTheme.typography.displaySmall
                )
                Text(
                    text = tempLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$otherLabel ${otherTemp}°C",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(modifier = Modifier.weight(1.4f)) {
                WeatherMetric(label = "UV", value = "${weather.uvIndex}", unit = "", modifier = Modifier.weight(1f))
                WeatherMetric(
                    label = "Wind",
                    value = "${weather.windSpeedKmh.toInt()}",
                    unit = " km/h",
                    modifier = Modifier.weight(1f)
                )
                WeatherMetric(
                    label = "Rain",
                    value = "${weather.precipitationProbabilityPercent}",
                    unit = " %",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** One centred column: the label above, the value (with a smaller unit) below. */
@Composable
private fun WeatherMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = buildAnnotatedString {
                append(value)
                withStyle(SpanStyle(fontSize = MaterialTheme.typography.labelSmall.fontSize)) { append(unit) }
            },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun NoRecommendationCard(displayTemp: Int, useApparentTemperature: Boolean) {
    val mode = if (useApparentTemperature) "feels-like" else "actual"
    Card(modifier = Modifier.fillMaxWidth(), colors = neutralCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "No outfit for $mode $displayTemp° yet",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Pick one yourself — once you rate it, it'll be recommended here next time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RainAheadCard(rain: RainAhead) {
    val title = if (rain.startsNow) "Rain likely now"
    else "Rain likely from ${rain.from.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    Card(modifier = Modifier.fillMaxWidth(), colors = neutralCardColors()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Umbrella,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Up to ${rain.peakPercent} % later today. Consider packing an umbrella or a rain jacket.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun UvInfoCard(uvIndex: Int) {
    Card(modifier = Modifier.fillMaxWidth(), colors = neutralCardColors()) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary
            )
            Column {
                Text(
                    text = "UV-Index $uvIndex",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Consider using sun glasses as well as UV sleeves and/or sun lotion.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Permission helpers ────────────────────────────────────────────────────

/** Walks the Context wrapper chain to find the hosting Activity, if any. */
private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Opens the system "App info" screen so the user can grant a permanently denied permission. */
private fun android.content.Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
    }
    startActivity(intent)
}
