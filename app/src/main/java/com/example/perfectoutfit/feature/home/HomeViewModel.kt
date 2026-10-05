package com.example.perfectoutfit.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.location.LocationResult
import com.example.perfectoutfit.core.location.LocationSource
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.feature.outfit.LogLocation
import com.example.perfectoutfit.feature.outfit.LogMode
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val locationSource: LocationSource,
    private val forecast: Forecast,
    private val liveOutfitHandoffStore: LiveOutfitHandoffStore,
    private val outfitLogging: OutfitLogging,
    private val recommendations: Recommendations,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * The in-flight current-location request. A new request cancels it, so a slow stale result
     * (location + reverse geocode + weather) can never overwrite a more recent one.
     */
    private var locationJob: Job? = null

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
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                loadingMessage = "Determining location...",
                error = null
            )
            when (val result = locationSource.current()) {
                is LocationResult.Found ->
                    fetchWeather(result.lat, result.lon, result.name.ifEmpty { "Current location" })
                LocationResult.PermissionDenied ->
                    failWithError("Location permission required. Please grant location access.")
                is LocationResult.Unavailable -> failWithError(result.message)
            }
        }
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
