package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.home.LiveOutfitHandoffStore
import com.example.perfectoutfit.feature.outfit.LogLocation
import com.example.perfectoutfit.feature.outfit.LogMode
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.recommendation.Recommendations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalDate
import javax.inject.Inject

enum class LogOutfitStep { DATE_TIME_LOCATION, OUTFIT_CATEGORIES, SUMMARY, RATING }

/** Alias to the single source of truth defined in BodyPart.kt. */
val LOG_OUTFIT_CATEGORY_ORDER = com.example.perfectoutfit.core.model.BODY_PART_DISPLAY_ORDER

sealed class OutfitScreenMode {
    data class RateExisting(val entryId: Long) : OutfitScreenMode()
    data object NewLive : OutfitScreenMode()
    data object NewPast : OutfitScreenMode()
}

data class RateOutfitUiState(
    val mode: OutfitScreenMode = OutfitScreenMode.NewPast,
    val isLoading: Boolean = true,
    val weatherSnapshot: WeatherSnapshot? = null,
    val sport: Sport = Sport.CYCLING,
    val selectedItemIds: Set<Long> = emptySet(),
    val comfortRating: Int? = null,
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
    val likelyItemIds: Set<Long> = emptySet(),
    val notes: String = "",
    val workoutDurationHours: Int = 1
) {
    val selectedHour: HourlyWeather?
        get() = hoursForSelectedDate.getOrNull(selectedHourIndex)
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
    private val preferencesManager: PreferencesManager
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

    private val _sport = MutableStateFlow(Sport.CYCLING)

    val availableItems: StateFlow<List<ClothingItem>> = _sport
        .flatMapLatest { sport -> catalogRepository.getItemsBySport(sport) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Likely items follow the selected hour, the sport and the (reactive) apparent/real
        // temperature preference.
        viewModelScope.launch {
            combine(
                _uiState.map { it.selectedHour to it.sport }.distinctUntilChanged(),
                preferencesManager.useApparentTemperature
            ) { (hour, sport), useApparent -> Triple(hour, sport, useApparent) }
                .collectLatest { (hour, sport, useApparent) ->
                    val ids = if (hour != null) {
                        recommendations.likelyItemIds(sport, hour.referenceTemp(useApparent), useApparent)
                    } else emptySet()
                    _uiState.update { it.copy(likelyItemIds = ids) }
                }
        }
        viewModelScope.launch {
            val sport = preferencesManager.selectedSport.first()
            _sport.value = sport
            _uiState.value = _uiState.value.copy(sport = sport)

            when (val mode = _uiState.value.mode) {
                is OutfitScreenMode.RateExisting -> {
                    val details = outfitLogging.getEntryWithDetails(mode.entryId)
                    if (details != null) {
                        _sport.value = details.entry.sport
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            weatherSnapshot = details.weatherSnapshot,
                            sport = details.entry.sport,
                            selectedItemIds = details.clothingItems.map { it.id }.toSet(),
                            comfortRating = details.entry.comfortRating,
                            notes = details.entry.notes
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(isLoading = false)
                    }
                }
                is OutfitScreenMode.NewLive -> {
                    val payload = liveOutfitHandoffStore.take()
                    val selectedTime = payload?.selectedHourTime
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
                    // GPS-only: there is no location picker anymore, so the wizard always uses
                    // the forecast's (current) location, same as the Home screen. Only dates up
                    // to today can be picked (the date picker's max date).
                    val today = forecast.today()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        hoursForSelectedDate = loadHours(today),
                        selectedDate = today,
                        selectedHourIndex = -1,
                        selectedItemIds = emptySet(),
                        logStep = LogOutfitStep.DATE_TIME_LOCATION,
                        logLocationSelected = true,
                        logLocationName = forecast.location?.name ?: ""
                    )
                }
            }
        }
    }

    /** Hours of [date] from the forecast; empty if unknown and the fetch fails. */
    private suspend fun loadHours(date: LocalDate): List<HourlyWeather> =
        try {
            forecast.hoursFor(date)
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

    fun setRating(rating: Int?) {
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
        viewModelScope.launch {
            val hours = loadHours(date)
            // Ignore the result if the user has picked another date in the meantime.
            if (_uiState.value.selectedDate != date) return@launch
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
                        location = LogLocation(state.logLocationName, forecast.location?.lat ?: 0.0, forecast.location?.lon ?: 0.0),
                        sport = state.sport,
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
