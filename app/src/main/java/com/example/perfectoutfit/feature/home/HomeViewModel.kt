package com.example.perfectoutfit.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.feature.outfit.LogLocation
import com.example.perfectoutfit.feature.outfit.LogMode
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import javax.inject.Inject

enum class WorkoutTab { COLDEST, WARMEST }

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadingMessage: String = "Loading...",
    /** Hours shown in the home screen (current hour → +24 h). */
    val hourlyWeather: List<HourlyWeather> = emptyList(),
    /** Index within hourlyWeather that the user has selected. */
    val selectedHourIndex: Int = 0,
    val selectedSport: Sport = Sport.CYCLING,
    val selectedLocationName: String = "Current location",
    val recommendation: OutfitEntryWithDetails? = null,
    val useApparentTemperature: Boolean = true,
    val error: String? = null,
    val workoutDurationHours: Int = 1,
    val activeWorkoutTab: WorkoutTab = WorkoutTab.COLDEST,
    val coldestHourIndex: Int = 0,
    val warmestHourIndex: Int = 0,
    val coldestRecommendation: OutfitEntryWithDetails? = null,
    val warmestRecommendation: OutfitEntryWithDetails? = null,
) {
    val selectedHour: HourlyWeather? get() = hourlyWeather.getOrNull(selectedHourIndex)

    val activeHourIndex: Int get() = when {
        workoutDurationHours <= 1 -> 0
        activeWorkoutTab == WorkoutTab.COLDEST -> coldestHourIndex
        else -> warmestHourIndex
    }
    val activeHour: HourlyWeather? get() = hourlyWeather.getOrNull(activeHourIndex)
    val activeRecommendation: OutfitEntryWithDetails? get() = when {
        workoutDurationHours <= 1 -> recommendation
        activeWorkoutTab == WorkoutTab.COLDEST -> coldestRecommendation
        else -> warmestRecommendation
    }
    val activeDisplayTemp: Int get() =
        activeHour?.referenceTemp(useApparentTemperature)?.roundToInt() ?: 0
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val forecast: Forecast,
    private val liveOutfitHandoffStore: LiveOutfitHandoffStore,
    private val outfitLogging: OutfitLogging,
    private val recommendations: Recommendations,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var locationCancellationSource: CancellationTokenSource? = null

    /**
     * Incremented every time the selected location changes. Async current-location
     * callbacks (FusedLocationProvider + reverse geocode) capture the value at request
     * time and apply their result only if it is still current, so a slow callback can no
     * longer overwrite a location the user has since switched to.
     */
    private var locationGeneration = 0

    init {
        // GPS is now the only source of location; kick off a fetch immediately.
        fetchCurrentLocationWeather()

        viewModelScope.launch {
            forecast.locationFlow.collect { location ->
                if (location != null) {
                    _uiState.value = _uiState.value.copy(selectedLocationName = location.name)
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.selectedSport.collect { sport ->
                _uiState.value = _uiState.value.copy(selectedSport = sport)
            }
        }
        viewModelScope.launch {
            preferencesManager.useApparentTemperature.collect { useApparent ->
                _uiState.value = _uiState.value.copy(useApparentTemperature = useApparent)
                refreshRecommendation()
            }
        }
    }

    fun selectSport(sport: Sport, workoutDurationHours: Int = 1) {
        _uiState.value = _uiState.value.copy(
            selectedSport = sport,
            workoutDurationHours = workoutDurationHours,
            activeWorkoutTab = WorkoutTab.COLDEST,
            recommendation = null,
            coldestRecommendation = null,
            warmestRecommendation = null,
            coldestHourIndex = -1,
            warmestHourIndex = -1
        )
        viewModelScope.launch {
            preferencesManager.setSelectedSport(sport)
            refreshRecommendation()
        }
    }

    fun selectWorkoutTab(tab: WorkoutTab) {
        _uiState.value = _uiState.value.copy(activeWorkoutTab = tab)
    }

    /** Accept recommendation: saves a new outfit entry and schedules a rating notification. */
    fun acceptRecommendation(recommendation: OutfitEntryWithDetails) {
        viewModelScope.launch {
            val weather = _uiState.value.activeHour ?: return@launch
            val location = forecast.location ?: return@launch
            outfitLogging.log(
                hour = weather,
                location = LogLocation(location.name, location.lat, location.lon),
                sport = _uiState.value.selectedSport,
                clothingItemIds = recommendation.clothingItems.map { it.id },
                workoutDurationHours = _uiState.value.workoutDurationHours,
                mode = LogMode.LIVE
            )
        }
    }

    fun prepareCustomOutfit() {
        liveOutfitHandoffStore.set(buildHandoffPayload(prefillItemIds = emptyList()))
    }

    fun prepareEditOutfit() {
        val itemIds = _uiState.value.activeRecommendation?.clothingItems?.map { it.id } ?: emptyList()
        liveOutfitHandoffStore.set(buildHandoffPayload(prefillItemIds = itemIds))
    }

    private fun buildHandoffPayload(prefillItemIds: List<Long>) = LiveOutfitPayload(
        selectedHourTime = _uiState.value.activeHour?.time,
        workoutDurationHours = _uiState.value.workoutDurationHours,
        prefillItemIds = prefillItemIds
    )

    private fun fetchCurrentLocationWeather() {
        // Each new fetch attempt invalidates any still-in-flight previous attempt, so a slow
        // stale callback can no longer overwrite the result of a more recent request.
        locationGeneration++

        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = "Location permission required. Please grant location access."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            loadingMessage = "Determining location...",
            error = null
        )

        locationCancellationSource?.cancel()
        val cancellationSource = CancellationTokenSource()
        locationCancellationSource = cancellationSource

        // Capture the generation at request time; lastLocation callbacks are not bound to
        // the cancellation token, so this is what stops a slow result from overwriting a
        // location the user has switched to in the meantime.
        val generation = locationGeneration

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // Try last known location first — instant cache hit, sufficient for city-level accuracy.
        fusedClient.lastLocation.addOnSuccessListener { lastLocation ->
            if (generation != locationGeneration) return@addOnSuccessListener
            if (lastLocation != null) {
                viewModelScope.launch {
                    applyCurrentLocation(lastLocation.latitude, lastLocation.longitude, generation)
                }
            } else {
                // No cached location; request a fresh one.
                requestFreshLocation(fusedClient, cancellationSource, generation)
            }
        }.addOnFailureListener {
            if (generation != locationGeneration) return@addOnFailureListener
            // lastLocation failed; fall through to getCurrentLocation.
            requestFreshLocation(fusedClient, cancellationSource, generation)
        }
    }

    private fun requestFreshLocation(
        fusedClient: FusedLocationProviderClient,
        cancellationSource: CancellationTokenSource,
        generation: Int
    ) {
        fusedClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            cancellationSource.token
        ).addOnSuccessListener { location ->
            if (generation != locationGeneration) return@addOnSuccessListener
            if (location != null) {
                viewModelScope.launch {
                    applyCurrentLocation(location.latitude, location.longitude, generation)
                }
            } else {
                failWithError("Could not determine location. Please try again.")
            }
        }.addOnFailureListener {
            if (generation != locationGeneration) return@addOnFailureListener
            failWithError("Location unavailable. Please try again.")
        }
    }

    private suspend fun applyCurrentLocation(lat: Double, lon: Double, generation: Int) {
        val cityName = reverseGeocode(lat, lon)
        // reverseGeocode is slow; bail out if the user has switched location since.
        if (generation != locationGeneration) return
        val displayName = cityName.ifEmpty { "Current location" }
        fetchWeather(lat, lon, displayName)
    }

    private fun failWithError(message: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isRefreshing = false,
            error = message
        )
    }

    private suspend fun fetchWeather(lat: Double, lon: Double, locationName: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = !_uiState.value.isRefreshing,
            loadingMessage = "Loading weather data...",
            error = null
        )

        try {
            forecast.refresh(lat, lon, locationName)
            val displayHours = forecast.displayWindow()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                hourlyWeather = displayHours,
                selectedHourIndex = 0,
                error = null
            )
            refreshRecommendation()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = "Failed to load weather: ${e.message}"
            )
        }
    }

    private suspend fun refreshRecommendation() {
        val sport = _uiState.value.selectedSport
        val useApparent = _uiState.value.useApparentTemperature
        val duration = _uiState.value.workoutDurationHours

        // Module discards the result if sport or duration changed while it queried.
        val window = recommendations.findForWorkoutWindow(
            sport = sport,
            hours = _uiState.value.hourlyWeather,
            durationHours = duration,
            useApparent = useApparent,
            isCurrent = {
                _uiState.value.selectedSport == sport && _uiState.value.workoutDurationHours == duration
            }
        ) ?: return

        _uiState.value = if (duration <= 1) {
            _uiState.value.copy(recommendation = window.coldest)
        } else {
            _uiState.value.copy(
                coldestHourIndex = window.coldestHourIndex,
                warmestHourIndex = window.warmestHourIndex,
                coldestRecommendation = window.coldest,
                warmestRecommendation = window.warmest
            )
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            cont.resume(
                                addresses.firstOrNull()?.locality
                                    ?: addresses.firstOrNull()?.subAdminArea
                                    ?: addresses.firstOrNull()?.adminArea
                                    ?: ""
                            )
                        }
                    }
                } else {
                    geocoder.getFromLocation(lat, lon, 1)
                        ?.firstOrNull()?.locality ?: ""
                }
            } catch (e: Exception) {
                ""
            }
        }

    /** Called when the screen resumes; retries weather load if a permission error is showing. */
    fun onLocationPermissionMaybeGranted() {
        if (_uiState.value.error?.contains("permission", ignoreCase = true) == true) {
            refreshWeather()
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val location = forecast.location
            if (location != null) {
                fetchWeather(location.lat, location.lon, location.name)
            } else {
                fetchCurrentLocationWeather()
            }
        }
    }
}
