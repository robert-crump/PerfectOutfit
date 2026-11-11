package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.model.Place

import androidx.lifecycle.ViewModel
import com.example.perfectoutfit.core.model.roundedReferenceTemp
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.location.LocationResult
import com.example.perfectoutfit.core.location.LocationSource
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.RainClass
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.feature.outfit.LogMode
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.feature.widget.WidgetForecastStore
import com.example.perfectoutfit.feature.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.io.IOException
import javax.inject.Inject

enum class WorkoutTab { COLDEST, WARMEST }

/** Where accepting a recommendation stands; Home leaves the result step on [SAVED]. */
enum class AcceptState { IDLE, SAVING, SAVED, FAILED }

/** Why Home has no weather to show; the screen renders [message] and reacts to the type. */
sealed interface HomeError {
    val message: String

    /** Location permission is missing: the screen asks for it, and resuming retries. */
    data object PermissionDenied : HomeError {
        override val message = "Location permission required. Please grant location access."
    }

    data class LocationUnavailable(override val message: String) : HomeError

    data class WeatherFailed(val reason: String?) : HomeError {
        override val message = "Failed to load weather: $reason"
    }
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadingMessage: String = "Loading...",
    /** Hours shown in the home screen (current hour → +24 h). */
    val hourlyWeather: List<HourlyWeather> = emptyList(),
    val selectedActivityType: ActivityType = ActivityType.CYCLING,
    val selectedLocationName: String = Place.DEFAULT_NAME,
    val useApparentTemperature: Boolean = true,
    val error: HomeError? = null,
    val workoutDurationHours: Int = HomeViewModel.SINGLE_HOUR,
    val activeWorkoutTab: WorkoutTab = WorkoutTab.COLDEST,
    val coldestHourIndex: Int = 0,
    val warmestHourIndex: Int = 0,
    val coldestRecommendation: OutfitEntryWithDetails? = null,
    val warmestRecommendation: OutfitEntryWithDetails? = null,
    val acceptState: AcceptState = AcceptState.IDLE,
    /** The outfit for the current hours is still being worked out; Home shows a spinner instead. */
    val isPreparingOutfit: Boolean = false,
) {
    /**
     * The one hour Home dresses for. On the result step it is the first hour, or a long workout's
     * coldest/warmest; on the activity grid (and for Explorer) it is the current hour.
     */
    val activeHourIndex: Int get() = when {
        workoutDurationHours <= HomeViewModel.SINGLE_HOUR -> 0
        activeWorkoutTab == WorkoutTab.COLDEST -> coldestHourIndex
        else -> warmestHourIndex
    }
    val activeHour: HourlyWeather? get() = hourlyWeather.getOrNull(activeHourIndex)
    val activeRecommendation: OutfitEntryWithDetails? get() = when {
        activeWorkoutTab == WorkoutTab.COLDEST || workoutDurationHours <= HomeViewModel.SINGLE_HOUR ->
            coldestRecommendation
        else -> warmestRecommendation
    }
    val activeDisplayTemp: Int get() =
        activeHour?.roundedReferenceTemp(useApparentTemperature) ?: 0

    /** The workout window's wettest rain class: what both recommendations were matched for. */
    val windowRainClass: RainClass get() = Recommendations.windowRainClass(hourlyWeather, workoutDurationHours)

    /** The current hour's rounded reference temperature: what the activity grid and Explorer show. */
    val currentDisplayTemp: Int? get() = displayTempAt(0)

    /** The rounded reference temperature of the hour at [index], or null if there is none. */
    fun displayTempAt(index: Int): Int? =
        hourlyWeather.getOrNull(index)?.roundedReferenceTemp(useApparentTemperature)
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val locationSource: LocationSource,
    private val forecast: Forecast,
    private val liveOutfitHandoffStore: LiveOutfitHandoffStore,
    private val outfitLogging: OutfitLogging,
    private val recommendations: Recommendations,
    private val preferencesManager: PreferencesManager,
    private val widgetForecastStore: WidgetForecastStore,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * The in-flight current-location request. A new request cancels it, so a slow stale result
     * (location + reverse geocode + weather) can never overwrite a more recent one.
     */
    private var locationJob: Job? = null

    /** A [catchUpWithClock] fetch is in flight; outfits worked out meanwhile keep the spinner up. */
    private var catchingUp = false

    init {
        // GPS is now the only source of location; kick off a fetch immediately.
        fetchCurrentLocationWeather()

        // A custom outfit saved in the live log wizard ends Home's flow like an accepted one.
        viewModelScope.launch {
            liveOutfitHandoffStore.outfitSaved.collect { saved ->
                if (saved && liveOutfitHandoffStore.takeSaved()) {
                    _uiState.value = _uiState.value.copy(acceptState = AcceptState.SAVED)
                }
            }
        }
        viewModelScope.launch {
            forecast.locationFlow.collect { location ->
                if (location != null) {
                    _uiState.value = _uiState.value.copy(selectedLocationName = location.name)
                }
            }
        }
        // selectedActivityType is deliberately not observed from preferences: a tile tap sets it
        // before the result step shows, and a late preference emission would overwrite that pick.
        viewModelScope.launch {
            preferencesManager.useApparentTemperature.collect { useApparent ->
                _uiState.value = _uiState.value.copy(useApparentTemperature = useApparent)
                refreshRecommendation()
            }
        }
    }

    fun selectActivityType(activityType: ActivityType, workoutDurationHours: Int = SINGLE_HOUR) {
        val duration = if (workoutDurationHours <= SINGLE_HOUR) SINGLE_HOUR
        else workoutDurationHours.coerceIn(MIN_LONG_WORKOUT_HOURS, MAX_LONG_WORKOUT_HOURS)
        // A widget tap can land on a Home kept alive since an earlier hour.
        val hours = currentDisplayWindow()
        val fetching = _uiState.value.isLoading || catchingUp
        _uiState.value = _uiState.value.copy(
            hourlyWeather = hours,
            isPreparingOutfit = true,
            // While weather is still on its way, the spinner keeps saying so.
            loadingMessage = if (fetching) _uiState.value.loadingMessage else PREPARING_MESSAGE,
            selectedActivityType = activityType,
            workoutDurationHours = duration,
            activeWorkoutTab = WorkoutTab.COLDEST,
            coldestRecommendation = null,
            warmestRecommendation = null,
            coldestHourIndex = -1,
            warmestHourIndex = -1
        )
        viewModelScope.launch {
            preferencesManager.setSelectedActivityType(activityType)
            refreshRecommendation()
        }
    }

    fun selectWorkoutTab(tab: WorkoutTab) {
        _uiState.value = _uiState.value.copy(activeWorkoutTab = tab)
    }

    /**
     * Accept recommendation: saves a new outfit entry and schedules a rating notification.
     * [HomeUiState.acceptState] reports the outcome; a second accept while one is saving is ignored.
     */
    fun acceptRecommendation(recommendation: OutfitEntryWithDetails) {
        val state = _uiState.value
        if (state.acceptState == AcceptState.SAVING) return
        val weather = state.activeHour
        val location = forecast.location
        if (weather == null || location == null) {
            _uiState.value = state.copy(acceptState = AcceptState.FAILED)
            return
        }
        _uiState.value = state.copy(acceptState = AcceptState.SAVING)
        viewModelScope.launch {
            val saved = try {
                outfitLogging.log(
                    hour = weather,
                    location = location,
                    activityType = state.selectedActivityType,
                    clothingItemIds = recommendation.clothingItems.map { it.id },
                    workoutDurationHours = state.workoutDurationHours,
                    mode = LogMode.LIVE
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            _uiState.value = _uiState.value.copy(
                acceptState = if (saved) AcceptState.SAVED else AcceptState.FAILED
            )
        }
    }

    /** Called once Home has shown a [AcceptState.SAVED] or [AcceptState.FAILED] outcome. */
    fun onAcceptHandled() {
        _uiState.value = _uiState.value.copy(acceptState = AcceptState.IDLE)
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
        activityType = _uiState.value.selectedActivityType,
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
                    fetchWeather(result.place.lat, result.place.lon, result.place.displayName)
                LocationResult.PermissionDenied ->
                    failWithError(HomeError.PermissionDenied)
                is LocationResult.Unavailable -> failWithError(HomeError.LocationUnavailable(result.message))
            }
        }
    }

    private fun failWithError(error: HomeError) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            isRefreshing = false,
            error = error
        )
    }

    private suspend fun fetchWeather(lat: Double, lon: Double, locationName: String) {
        // A pull to refresh keeps the shown outfit until the new one is ready.
        val keepOutfit = _uiState.value.isRefreshing
        _uiState.value = _uiState.value.copy(
            isLoading = !_uiState.value.isRefreshing,
            loadingMessage = "Loading weather data...",
            error = null
        )

        try {
            forecast.refresh(lat, lon, locationName)
            saveForWidget()
            val displayHours = forecast.displayWindow()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                hourlyWeather = displayHours,
                isPreparingOutfit = !keepOutfit,
                loadingMessage = PREPARING_MESSAGE,
                error = null
            )
            refreshRecommendation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = HomeError.WeatherFailed(e.message)
            )
        }
    }

    /** The widget follows the last place Home resolved; a failed save only leaves it on the previous one. */
    private suspend fun saveForWidget() {
        val location = forecast.location ?: return
        try {
            widgetForecastStore.save(location, forecast.hours.first())
            widgetUpdater.redraw()
        } catch (e: IOException) {
            // Keep showing the weather; the widget catches up on the next refresh.
        }
    }

    private suspend fun refreshRecommendation() {
        val activityType = _uiState.value.selectedActivityType
        val useApparent = _uiState.value.useApparentTemperature
        val duration = _uiState.value.workoutDurationHours
        val hours = _uiState.value.hourlyWeather
        val isCurrent = {
            _uiState.value.selectedActivityType == activityType &&
                _uiState.value.workoutDurationHours == duration &&
                _uiState.value.hourlyWeather === hours
        }

        // Discarded if activityType, duration or the hours changed while it queried: a newer
        // refresh is then on its way and settles the spinner instead.
        val window = recommendations.findForWorkoutWindow(
            activityType = activityType,
            hours = hours,
            durationHours = duration,
            useApparent = useApparent,
            isCurrent = isCurrent
        )
        if (!isCurrent()) return

        val state = _uiState.value.copy(isPreparingOutfit = catchingUp)
        _uiState.value = when {
            window == null -> state
            duration <= SINGLE_HOUR -> state.copy(coldestRecommendation = window.coldest)
            else -> state.copy(
                coldestHourIndex = window.coldestHourIndex,
                warmestHourIndex = window.warmestHourIndex,
                coldestRecommendation = window.coldest,
                warmestRecommendation = window.warmest
            )
        }
    }

    /** Called when the screen resumes; retries weather load if a permission error is showing. */
    fun onLocationPermissionMaybeGranted() {
        if (_uiState.value.error == HomeError.PermissionDenied) {
            refreshWeather()
        }
    }

    /**
     * Called when the screen resumes: moves the shown hours up to the current clock hour, first
     * fetching a new forecast if the known one is [Forecast.STALE_AFTER] old. The outfit stays
     * hidden until it matches; a failed fetch falls back to the known forecast.
     */
    fun catchUpWithClock() {
        val state = _uiState.value
        val location = forecast.location
        // Nothing shown yet, or a fetch is already on its way.
        if (location == null || state.isLoading || state.isRefreshing || catchingUp) return
        val stale = forecast.isStale()
        val hourMoved = currentDisplayWindow().firstOrNull()?.time != state.hourlyWeather.firstOrNull()?.time
        if (!stale && !hourMoved) return

        _uiState.value = state.copy(
            isPreparingOutfit = true,
            loadingMessage = if (stale) "Updating weather..." else PREPARING_MESSAGE
        )
        if (stale) catchingUp = true
        viewModelScope.launch {
            if (stale) {
                try {
                    forecast.refresh(location.lat, location.lon, location.name)
                    saveForWidget()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Offline: the known forecast, moved up to the current hour, still serves.
                } finally {
                    catchingUp = false
                }
            }
            _uiState.value = _uiState.value.copy(
                hourlyWeather = currentDisplayWindow(),
                coldestHourIndex = -1,
                warmestHourIndex = -1
            )
            refreshRecommendation()
        }
    }

    /** The forecast's window from the current clock hour, or the shown hours while none is known. */
    private fun currentDisplayWindow(): List<HourlyWeather> =
        if (forecast.location == null) _uiState.value.hourlyWeather else forecast.displayWindow()

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

    companion object {
        /** A workout of one hour needs no long-workout picker. */
        const val SINGLE_HOUR = 1
        const val MIN_LONG_WORKOUT_HOURS = 2
        const val MAX_LONG_WORKOUT_HOURS = 23
        const val DEFAULT_LONG_WORKOUT_HOURS = MIN_LONG_WORKOUT_HOURS

        private const val PREPARING_MESSAGE = "Finding your outfit..."
    }
}
