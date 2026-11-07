package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.inHeadToToeOrder
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.core.network.GeocodingApi
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.home.LiveOutfitHandoffStore
import com.example.perfectoutfit.feature.outfit.LogLocation
import com.example.perfectoutfit.feature.outfit.LogMode
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.feature.outfit.RecentPlace
import com.example.perfectoutfit.feature.outfit.recentPlaces
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.recommendation.Recommendations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

enum class LogOutfitStep { DATE_TIME_LOCATION, OUTFIT_CATEGORIES, SUMMARY, RATING }

/** A city search hit: where it is, plus "Region, Country" to tell same-named towns apart. */
data class PlaceSearchResult(val location: LogLocation, val region: String)

/** Typing pause before a city search runs. */
const val PLACE_SEARCH_DEBOUNCE_MS = 300L

/** Characters needed before a city search runs. */
const val PLACE_SEARCH_MIN_CHARS = 2

/** The user-editable parts of an existing entry. */
data class OutfitEdits(val itemIds: Set<Long>, val comfortRating: ComfortRating?, val notes: String)

sealed class OutfitScreenMode {
    data class RateExisting(val entryId: Long) : OutfitScreenMode()
    data object NewLive : OutfitScreenMode()
    data object NewPast : OutfitScreenMode()
}

data class RateOutfitUiState(
    val mode: OutfitScreenMode = OutfitScreenMode.NewPast,
    val isLoading: Boolean = true,
    val weatherSnapshot: WeatherSnapshot? = null,
    /** The workout hour of the entry being rated (its `createdAt`; rate-existing mode only). */
    val workoutTime: LocalDateTime? = null,
    val activityType: ActivityType = ActivityType.CYCLING,
    /** Which temperature leads the detail card's weather (the user's preference). */
    val useApparentTemperature: Boolean = true,
    val selectedItemIds: Set<Long> = emptySet(),
    val comfortRating: ComfortRating? = null,
    val isSaved: Boolean = false,
    /** Known hours of [selectedDate]. */
    val hoursForSelectedDate: List<HourlyWeather> = emptyList(),
    val selectedDate: LocalDate = LocalDate.MIN,
    /** Index into [hoursForSelectedDate]; -1 = none picked. */
    val selectedHourIndex: Int = 0,
    // Wizard state (new outfit modes only)
    val logStep: LogOutfitStep = LogOutfitStep.DATE_TIME_LOCATION,
    val logCategoryIndex: Int = 0,
    val showDismissDialog: Boolean = false,
    val isLoadingLocationWeather: Boolean = false,
    val isLoadingDateWeather: Boolean = false,
    val logLocationSelected: Boolean = false,
    val logLocationName: String = "",
    /** The place a past outfit is logged at; null = the current (GPS) location. */
    val chosenLocation: LogLocation? = null,
    val showLocationSheet: Boolean = false,
    val recentPlaces: List<RecentPlace> = emptyList(),
    val locationQuery: String = "",
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val searchFailed: Boolean = false,
    val likelyItemIds: Set<Long> = emptySet(),
    val notes: String = "",
    val workoutDurationHours: Int = 1,
    /** The entry as loaded (rate-existing mode only), to detect unsaved edits. */
    val savedEdits: OutfitEdits? = null
) {
    val selectedHour: HourlyWeather?
        get() = hoursForSelectedDate.getOrNull(selectedHourIndex)

    val hasUnsavedEdits: Boolean
        get() = savedEdits != null && savedEdits != OutfitEdits(selectedItemIds, comfortRating, notes)
}

/**
 * The items an outfit can be built from: the activity type's catalog plus any item already on
 * the entry, so an item later untagged from that activity type is never hidden or silently
 * dropped on save.
 */
internal fun itemsToOffer(catalogItems: List<ClothingItem>, entryItems: List<ClothingItem>): List<ClothingItem> {
    val catalogIds = catalogItems.mapTo(HashSet()) { it.id }
    val extra = entryItems.filter { it.id !in catalogIds }
    // Head to toe, so extras sit among their peers.
    return (catalogItems + extra).inHeadToToeOrder()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RateOutfitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val outfitLogging: OutfitLogging,
    private val recommendations: Recommendations,
    private val catalogRepository: CatalogRepository,
    private val forecast: Forecast,
    private val liveOutfitHandoffStore: LiveOutfitHandoffStore,
    private val preferencesManager: PreferencesManager,
    private val geocodingApi: GeocodingApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        RateOutfitUiState(
            selectedDate = forecast.today(),
            mode = run {
                val entryId = savedStateHandle.get<Long>("outfitEntryId")
                val isLive = savedStateHandle.get<Boolean>("isLive") ?: false
                when {
                    entryId != null && entryId > 0 -> OutfitScreenMode.RateExisting(entryId)
                    isLive -> OutfitScreenMode.NewLive
                    else -> OutfitScreenMode.NewPast
                }
            }
        )
    )
    val uiState: StateFlow<RateOutfitUiState> = _uiState.asStateFlow()

    private val _activityType = MutableStateFlow(ActivityType.CYCLING)

    /** Items already on the entry being edited; empty for new outfits. */
    private val _entryItems = MutableStateFlow<List<ClothingItem>>(emptyList())

    val availableItems: StateFlow<List<ClothingItem>> = _activityType
        .flatMapLatest { activityType -> catalogRepository.getItemsByActivityType(activityType) }
        .combine(_entryItems, ::itemsToOffer)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val locationQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            locationQuery.collectLatest { query -> search(query.trim()) }
        }
        viewModelScope.launch {
            preferencesManager.useApparentTemperature.collect { useApparent ->
                _uiState.update { it.copy(useApparentTemperature = useApparent) }
            }
        }
        // Likely items follow the selected hour, the activityType and the (reactive) apparent/real
        // temperature preference.
        viewModelScope.launch {
            combine(
                _uiState.map { it.selectedHour to it.activityType }.distinctUntilChanged(),
                preferencesManager.useApparentTemperature
            ) { (hour, activityType), useApparent -> Triple(hour, activityType, useApparent) }
                .collectLatest { (hour, activityType, useApparent) ->
                    val ids = if (hour != null) {
                        recommendations.likelyItemIds(activityType, hour.referenceTemp(useApparent), useApparent)
                    } else emptySet()
                    _uiState.update { it.copy(likelyItemIds = ids) }
                }
        }
        viewModelScope.launch {
            val activityType = preferencesManager.selectedActivityType.first()
            _activityType.value = activityType
            _uiState.value = _uiState.value.copy(activityType = activityType)

            when (val mode = _uiState.value.mode) {
                is OutfitScreenMode.RateExisting -> {
                    val details = outfitLogging.getEntryWithDetails(mode.entryId)
                    if (details != null) {
                        _activityType.value = details.entry.activityType
                        _entryItems.value = details.clothingItems
                        val edits = OutfitEdits(
                            itemIds = details.clothingItems.map { it.id }.toSet(),
                            comfortRating = details.entry.rating,
                            notes = details.entry.notes
                        )
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            weatherSnapshot = details.weatherSnapshot,
                            workoutTime = LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(details.entry.createdAt), ZoneId.systemDefault()
                            ),
                            activityType = details.entry.activityType,
                            selectedItemIds = edits.itemIds,
                            comfortRating = edits.comfortRating,
                            notes = edits.notes,
                            savedEdits = edits
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
                is OutfitScreenMode.NewLive -> {
                    val payload = liveOutfitHandoffStore.take()
                    val selectedTime = payload?.selectedHourTime
                    // The handoff's activity type, never the DataStore value Home writes asynchronously.
                    if (payload != null) {
                        _activityType.value = payload.activityType
                        _uiState.value = _uiState.value.copy(activityType = payload.activityType)
                    }
                    val date = selectedTime?.toLocalDate() ?: forecast.today()
                    val hours = loadHours(date)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hoursForSelectedDate = hours,
                        selectedDate = date,
                        selectedHourIndex = hours.indexOfFirst { it.time == selectedTime }
                            .coerceAtLeast(0),
                        selectedItemIds = payload?.prefillItemIds?.toSet() ?: emptySet(),
                        logStep = LogOutfitStep.OUTFIT_CATEGORIES,
                        logLocationSelected = true,
                        logLocationName = forecast.location?.name ?: "",
                        workoutDurationHours = payload?.workoutDurationHours ?: 1
                    )
                }
                is OutfitScreenMode.NewPast -> {
                    // Starts at the forecast's (current) location; the location sheet can switch
                    // to a recent or searched place. Only dates up to today can be picked (the
                    // date picker's max date).
                    val today = forecast.today()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hoursForSelectedDate = loadHours(today),
                        selectedDate = today,
                        selectedHourIndex = -1,
                        selectedItemIds = emptySet(),
                        logStep = LogOutfitStep.DATE_TIME_LOCATION,
                        logLocationSelected = true,
                        logLocationName = forecast.location?.name ?: "",
                        recentPlaces = recentPlaces(
                            outfitLogging.allEntries().first().map { it.weatherSnapshot },
                            forecast.location
                        )
                    )
                }
            }
        }
    }

    /**
     * Hours of [date] at [at] (null = the current location, from the forecast); empty if unknown
     * and the fetch fails.
     */
    private suspend fun loadHours(
        date: LocalDate,
        at: LogLocation? = _uiState.value.chosenLocation
    ): List<HourlyWeather> =
        try {
            if (at == null) forecast.hoursFor(date) else forecast.hoursAt(at.lat, at.lon, date)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    fun toggleItem(itemId: Long) {
        val current = _uiState.value.selectedItemIds
        _uiState.value = _uiState.value.copy(
            selectedItemIds = if (itemId in current) current - itemId else current + itemId
        )
    }

    fun setRating(rating: ComfortRating?) {
        _uiState.value = _uiState.value.copy(comfortRating = rating)
    }

    fun setNotes(text: String) {
        _uiState.value = _uiState.value.copy(notes = text)
    }

    fun selectDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            hoursForSelectedDate = emptyList(),
            selectedHourIndex = -1,
            isLoadingDateWeather = true
        )
        val at = _uiState.value.chosenLocation
        viewModelScope.launch {
            val hours = loadHours(date, at)
            // Ignore the result if the user has picked another date or place in the meantime.
            if (_uiState.value.selectedDate != date || _uiState.value.chosenLocation != at) return@launch
            _uiState.value = _uiState.value.copy(
                isLoadingDateWeather = false,
                hoursForSelectedDate = hours
            )
        }
    }

    /** Called after the user confirms a time in the Android TimePickerDialog. */
    fun selectHourByClockHour(hour: Int) {
        val hours = _uiState.value.hoursForSelectedDate
        val match = Forecast.hourNearest(hours, hour) ?: return
        _uiState.value = _uiState.value.copy(selectedHourIndex = hours.indexOf(match))
    }

    // ─── Location picker (past logging) ──────────────────────────────────────

    fun openLocationSheet() {
        _uiState.update { it.copy(showLocationSheet = true) }
    }

    fun dismissLocationSheet() {
        locationQuery.value = ""
        _uiState.update { it.copy(showLocationSheet = false, locationQuery = "") }
    }

    fun setLocationQuery(query: String) {
        _uiState.update { it.copy(locationQuery = query) }
        locationQuery.value = query
    }

    /**
     * Switches the place the outfit is logged at ([place] null = the current location) and
     * fetches its weather for the selected date, keeping the picked clock hour.
     */
    fun selectLocation(place: LogLocation?) {
        val state = _uiState.value
        val date = state.selectedDate
        val clockHour = state.selectedHour?.time?.hour
        locationQuery.value = ""
        _uiState.value = state.copy(
            chosenLocation = place,
            logLocationName = place?.name ?: forecast.location?.name ?: "",
            showLocationSheet = false,
            locationQuery = "",
            hoursForSelectedDate = emptyList(),
            selectedHourIndex = -1,
            isLoadingLocationWeather = true
        )
        viewModelScope.launch {
            val hours = loadHours(date, place)
            // Ignore the result if the user has picked another date or place in the meantime.
            if (_uiState.value.selectedDate != date || _uiState.value.chosenLocation != place) return@launch
            val hour = clockHour?.let { Forecast.hourNearest(hours, it) }
            _uiState.update {
                it.copy(
                    isLoadingLocationWeather = false,
                    hoursForSelectedDate = hours,
                    selectedHourIndex = hour?.let(hours::indexOf) ?: -1
                )
            }
        }
    }

    /** Runs a city search for [query] once typing pauses; cancelled by the next keystroke. */
    private suspend fun search(query: String) {
        if (query.length < PLACE_SEARCH_MIN_CHARS) {
            _uiState.update { it.copy(searchResults = emptyList(), searchFailed = false) }
            return
        }
        delay(PLACE_SEARCH_DEBOUNCE_MS)
        val results = try {
            geocodingApi.search(query).results.map { r ->
                PlaceSearchResult(
                    location = LogLocation(r.name, r.latitude, r.longitude),
                    region = listOf(r.admin1, r.country).filter { it.isNotBlank() }.joinToString(", ")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _uiState.update { it.copy(searchResults = results.orEmpty(), searchFailed = results == null) }
    }

    // ─── Wizard navigation ───────────────────────────────────────────────────

    fun advanceToCategories() {
        _uiState.value = _uiState.value.copy(
            logStep = LogOutfitStep.OUTFIT_CATEGORIES,
            logCategoryIndex = 0
        )
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
                if (_uiState.value.mode is OutfitScreenMode.NewLive) showDismissDialog()
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

    // ─── Save ────────────────────────────────────────────────────────────────

    fun save() {
        viewModelScope.launch {
            val state = _uiState.value
            when (val mode = state.mode) {
                is OutfitScreenMode.RateExisting -> outfitLogging.update(
                    entryId = mode.entryId,
                    clothingItemIds = state.selectedItemIds,
                    notes = state.notes,
                    rating = state.comfortRating
                )
                is OutfitScreenMode.NewLive, is OutfitScreenMode.NewPast -> {
                    val selectedHour = state.selectedHour
                    if (selectedHour == null || state.selectedItemIds.isEmpty()) return@launch

                    outfitLogging.log(
                        hour = selectedHour,
                        location = state.chosenLocation ?: LogLocation(
                            state.logLocationName, forecast.location?.lat ?: 0.0, forecast.location?.lon ?: 0.0
                        ),
                        activityType = state.activityType,
                        clothingItemIds = state.selectedItemIds,
                        rating = state.comfortRating,
                        notes = state.notes,
                        workoutDurationHours = state.workoutDurationHours,
                        mode = if (mode is OutfitScreenMode.NewLive) LogMode.LIVE else LogMode.PAST
                    )
                }
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
