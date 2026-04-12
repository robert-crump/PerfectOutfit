package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.FavoriteLocation
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.notification.NotificationHelper
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.home.OutfitRepository
import com.example.perfectoutfit.feature.home.WeatherRepository
import com.example.perfectoutfit.feature.location.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class LogOutfitStep { DATE_TIME_LOCATION, OUTFIT_CATEGORIES, SUMMARY, RATING }

/** Alias to the single source of truth defined in BodyPart.kt. */
val LOG_OUTFIT_CATEGORY_ORDER = com.example.perfectoutfit.core.model.BODY_PART_DISPLAY_ORDER

data class RateOutfitUiState(
    val isLoading: Boolean = true,
    val outfitEntryId: Long? = null,
    val weatherSnapshot: WeatherSnapshot? = null,
    val sport: Sport = Sport.CYCLING,
    val selectedItemIds: Set<Long> = emptySet(),
    val comfortRating: Int? = null,
    val isSaved: Boolean = false,
    // New-outfit mode: hour selection from cached weather
    val isNewOutfitMode: Boolean = false,
    val availableHours: List<HourlyWeather> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedHourIndex: Int = 0,
    // Wizard state (new outfit mode only)
    val logStep: LogOutfitStep = LogOutfitStep.DATE_TIME_LOCATION,
    val logCategoryIndex: Int = 0,
    val showDismissDialog: Boolean = false,
    val isLoadingLocationWeather: Boolean = false,
    val isLoadingDateWeather: Boolean = false,
    val logLocationSelected: Boolean = false,
    val logLocationName: String = "",
    val logFavLocations: List<FavoriteLocation> = emptyList(),
    val logLat: Double = 0.0,
    val logLon: Double = 0.0,
    val likelyItemIds: Set<Long> = emptySet(),
    val isLiveMode: Boolean = false,
    val notes: String = ""
) {
    val selectedHour: HourlyWeather?
        get() = availableHours.getOrNull(selectedHourIndex)

    val hoursForSelectedDate: List<HourlyWeather>
        get() = availableHours.filter { it.time.toLocalDate() == selectedDate }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RateOutfitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val outfitRepository: OutfitRepository,
    private val catalogRepository: CatalogRepository,
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(RateOutfitUiState())
    val uiState: StateFlow<RateOutfitUiState> = _uiState.asStateFlow()

    private val _sport = MutableStateFlow(Sport.CYCLING)

    val availableItems: StateFlow<List<ClothingItem>> = _sport
        .flatMapLatest { sport -> catalogRepository.getItemsBySport(sport) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val entryId = savedStateHandle.get<Long>("outfitEntryId")
        val isLive = savedStateHandle.get<Boolean>("isLive") ?: false
        viewModelScope.launch {
            val sport = preferencesManager.selectedSport.first()
            _sport.value = sport
            _uiState.value = _uiState.value.copy(sport = sport)

            if (entryId != null && entryId > 0) {
                // Rating an existing outfit entry
                val details = outfitRepository.getEntryWithDetails(entryId)
                if (details != null) {
                    _sport.value = details.entry.sport
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        outfitEntryId = entryId,
                        weatherSnapshot = details.weatherSnapshot,
                        sport = details.entry.sport,
                        selectedItemIds = details.clothingItems.map { it.id }.toSet(),
                        comfortRating = details.entry.comfortRating,
                        isNewOutfitMode = false,
                        notes = details.entry.notes
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            } else if (isLive) {
                // Live outfit mode: location/date/time pre-populated from Home Screen cache
                val allHours = weatherRepository.cachedAllHours
                val selTime = weatherRepository.cachedSelectedHourTime
                val matchIdx = if (selTime != null)
                    allHours.indexOfFirst { it.time == selTime }.takeIf { it >= 0 } ?: 0
                else 0
                val selHour = allHours.getOrNull(matchIdx)
                val useApp = preferencesManager.useApparentTemperature.first()
                val likelyIds = if (selHour != null) {
                    val temp = if (useApp) selHour.apparentTemperatureCelsius
                               else selHour.temperatureCelsius
                    outfitRepository.getLikelyItemIds(sport, temp, useApp)
                } else emptySet()
                val favorites = locationRepository.getAllFavoritesSync()

                val pendingIds = weatherRepository.pendingNewOutfitItemIds.toSet()
                weatherRepository.pendingNewOutfitItemIds = emptyList()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isNewOutfitMode = true,
                    isLiveMode = true,
                    availableHours = allHours,
                    selectedDate = LocalDate.now(),
                    selectedHourIndex = matchIdx,
                    selectedItemIds = pendingIds,
                    logStep = LogOutfitStep.OUTFIT_CATEGORIES,
                    logLocationSelected = true,
                    logLocationName = weatherRepository.cachedLocationName.ifEmpty { "Current Location" },
                    logLat = weatherRepository.cachedLat,
                    logLon = weatherRepository.cachedLon,
                    logFavLocations = favorites,
                    likelyItemIds = likelyIds
                )
            } else {
                // Past outfit / Log outfit mode: load cached hourly weather + pre-filled items
                val today = LocalDate.now()
                val favorites = locationRepository.getAllFavoritesSync()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isNewOutfitMode = true,
                    availableHours = emptyList(), // populated only after city is selected
                    selectedDate = today,
                    selectedHourIndex = -1, // no hour pre-selected; user picks via time picker
                    selectedItemIds = emptySet(), // no clothing items pre-selected
                    logStep = LogOutfitStep.DATE_TIME_LOCATION,
                    logLocationSelected = false,
                    logLocationName = "",
                    logFavLocations = favorites,
                    logLat = 0.0,
                    logLon = 0.0
                )
                weatherRepository.pendingNewOutfitItemIds = emptyList()
            }
        }
    }

    fun toggleItem(itemId: Long) {
        val current = _uiState.value.selectedItemIds
        _uiState.value = _uiState.value.copy(
            selectedItemIds = if (itemId in current) current - itemId else current + itemId
        )
    }

    fun setRating(rating: Int?) {
        _uiState.value = _uiState.value.copy(comfortRating = rating)
    }

    fun setNotes(text: String) {
        _uiState.value = _uiState.value.copy(notes = text)
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            selectedHourIndex = -1
        )
        // If we don't have weather data for this date yet, fetch it now.
        val hasHours = _uiState.value.availableHours.any { it.time.toLocalDate() == date }
        if (!hasHours) {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(isLoadingDateWeather = true)
                try {
                    val newHours = weatherRepository.fetchWeatherForDate(
                        _uiState.value.logLat, _uiState.value.logLon, date
                    )
                    val merged = (_uiState.value.availableHours
                        .filter { it.time.toLocalDate() != date } + newHours)
                        .sortedBy { it.time }
                    _uiState.value = _uiState.value.copy(
                        isLoadingDateWeather = false,
                        availableHours = merged
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(isLoadingDateWeather = false)
                }
            }
        }
    }

    /** Called after the user confirms a time in the Android TimePickerDialog. */
    fun selectHourByClockHour(hour: Int) {
        val hoursForDate = _uiState.value.hoursForSelectedDate
        // Find the entry whose hour matches, or the last one before it as fallback.
        val match = hoursForDate.firstOrNull { it.time.hour == hour }
            ?: hoursForDate.lastOrNull { it.time.hour < hour }
            ?: hoursForDate.firstOrNull()
        val globalIndex = match?.let { _uiState.value.availableHours.indexOf(it) } ?: -1
        if (globalIndex >= 0) {
            _uiState.value = _uiState.value.copy(selectedHourIndex = globalIndex)
        }
    }

    fun selectHour(indexInDay: Int) {
        val hoursForDate = _uiState.value.hoursForSelectedDate
        val globalIndex = _uiState.value.availableHours.indexOf(hoursForDate.getOrNull(indexInDay))
        if (globalIndex >= 0) {
            _uiState.value = _uiState.value.copy(selectedHourIndex = globalIndex)
        }
    }

    // ─── Wizard navigation ───────────────────────────────────────────────────

    fun advanceToCategories() {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedHour = state.selectedHour
            val likelyIds = if (selectedHour != null) {
                val useApparent = preferencesManager.useApparentTemperature.first()
                val temp = if (useApparent) selectedHour.apparentTemperatureCelsius
                           else selectedHour.temperatureCelsius
                outfitRepository.getLikelyItemIds(state.sport, temp, useApparent)
            } else emptySet()
            _uiState.value = _uiState.value.copy(
                logStep = LogOutfitStep.OUTFIT_CATEGORIES,
                logCategoryIndex = 0,
                likelyItemIds = likelyIds
            )
        }
    }

    fun advanceToSummary() {
        _uiState.value = _uiState.value.copy(logStep = LogOutfitStep.SUMMARY)
    }

    fun goBackFromSummary() {
        _uiState.value = _uiState.value.copy(logStep = LogOutfitStep.OUTFIT_CATEGORIES)
    }

    fun advanceToRating() {
        _uiState.value = _uiState.value.copy(logStep = LogOutfitStep.RATING)
    }

    fun goBackFromRating() {
        _uiState.value = _uiState.value.copy(logStep = LogOutfitStep.SUMMARY)
    }

    fun skipRating() {
        _uiState.value = _uiState.value.copy(comfortRating = null)
        save()
    }

    /** General "handle back" for the wizard's top-bar back arrow. */
    fun handleWizardBack() {
        when (_uiState.value.logStep) {
            LogOutfitStep.DATE_TIME_LOCATION -> showDismissDialog()
            LogOutfitStep.OUTFIT_CATEGORIES ->
                if (_uiState.value.isLiveMode) showDismissDialog()
                else _uiState.value = _uiState.value.copy(logStep = LogOutfitStep.DATE_TIME_LOCATION)
            LogOutfitStep.SUMMARY -> goBackFromSummary()
            LogOutfitStep.RATING -> goBackFromRating()
        }
    }

    fun showDismissDialog() {
        _uiState.value = _uiState.value.copy(showDismissDialog = true)
    }

    fun hideDismissDialog() {
        _uiState.value = _uiState.value.copy(showDismissDialog = false)
    }

    // ─── Location selection in wizard ────────────────────────────────────────

    fun selectLogLocation(location: FavoriteLocation?) {
        if (location == null) {
            // Current Location: use cached weather data.
            val today = LocalDate.now()
            val relevantHours = weatherRepository.cachedAllHours
                .filter { !it.time.toLocalDate().isAfter(today) }
            _uiState.value = _uiState.value.copy(
                logLocationSelected = true,
                logLocationName = weatherRepository.cachedLocationName.ifEmpty { "Current Location" },
                logLat = weatherRepository.cachedLat,
                logLon = weatherRepository.cachedLon,
                availableHours = relevantHours,
                selectedDate = today,
                selectedHourIndex = -1
            )
        } else {
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isLoadingLocationWeather = true,
                    logLocationSelected = true,
                    logLocationName = location.name
                )
                try {
                    val allHours = weatherRepository.fetchWeatherOnly(
                        location.latitude, location.longitude
                    )
                    val today = LocalDate.now()
                    val relevantHours = allHours.filter { !it.time.toLocalDate().isAfter(today) }
                    _uiState.value = _uiState.value.copy(
                        isLoadingLocationWeather = false,
                        logLat = location.latitude,
                        logLon = location.longitude,
                        availableHours = relevantHours,
                        selectedDate = today,
                        selectedHourIndex = -1
                    )
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingLocationWeather = false,
                        logLocationSelected = false,
                        logLocationName = ""
                    )
                }
            }
        }
    }

    // ─── Save ────────────────────────────────────────────────────────────────

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            val entryId = state.outfitEntryId
            if (entryId != null) {
                // Rating existing entry
                outfitRepository.updateEntryItems(entryId, state.selectedItemIds.toList())
                outfitRepository.updateNotes(entryId, state.notes)
                if (state.comfortRating != null) {
                    outfitRepository.rateEntry(entryId, state.comfortRating)
                }
            } else {
                // Creating new outfit entry via wizard
                val selectedHour = state.selectedHour
                if (selectedHour == null || state.selectedItemIds.isEmpty()) return@launch

                val snapshot = WeatherSnapshot(
                    timestamp = selectedHour.time
                        .atZone(ZoneId.systemDefault()).toEpochSecond() * 1000,
                    latitude = state.logLat,
                    longitude = state.logLon,
                    locationName = state.logLocationName,
                    temperatureCelsius = selectedHour.temperatureCelsius,
                    apparentTemperatureCelsius = selectedHour.apparentTemperatureCelsius,
                    windSpeedKmh = selectedHour.windSpeedKmh,
                    windDirectionDegrees = selectedHour.windDirectionDegrees,
                    uvIndex = selectedHour.uvIndex,
                    cloudCoverPercent = selectedHour.cloudCoverPercent,
                    precipitationProbabilityPercent = selectedHour.precipitationProbabilityPercent
                )
                val snapshotId = weatherRepository.saveSnapshot(snapshot)
                val workoutTimestamp = selectedHour.time
                    .atZone(ZoneId.systemDefault()).toEpochSecond() * 1000
                val newEntryId = outfitRepository.createEntry(
                    entry = OutfitEntry(
                        weatherSnapshotId = snapshotId,
                        sport = state.sport,
                        comfortRating = state.comfortRating,
                        createdAt = workoutTimestamp,
                        ratedAt = if (state.comfortRating != null) System.currentTimeMillis() else null,
                        notes = state.notes
                    ),
                    clothingItemIds = state.selectedItemIds.toList()
                )
                if (state.isLiveMode || state.comfortRating == null) {
                    notificationHelper.showRatingNotification(
                        outfitEntryId = newEntryId,
                        sport = state.sport,
                        dateMs = workoutTimestamp,
                        durationHours = weatherRepository.cachedWorkoutDurationHours
                    )
                }
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
