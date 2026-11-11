package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.Place
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.core.network.GeocodingApi
import com.example.perfectoutfit.core.time.currentHour
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.forecast.PlacedHours
import com.example.perfectoutfit.feature.home.LiveOutfitHandoffStore
import com.example.perfectoutfit.feature.outfit.LogMode
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.feature.outfit.recentPlaces
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class LogOutfitUiState(
    /** The wizard; null while its first hours load. */
    val wizard: LogWizardState? = null,
    val activityType: ActivityType = ActivityType.CYCLING,
    /** Items the recommendation expects at the picked hour (a hint for the items step). */
    val likelyItemIds: Set<Long> = emptySet(),
    val saveState: SaveState = SaveState.IDLE
) {
    val isSaved: Boolean get() = saveState == SaveState.SAVED
}

/** Where logging the wizard's outfit stands; Next is ignored while saving and once saved. */
enum class SaveState { IDLE, SAVING, SAVED, FAILED }

/**
 * Logging a new outfit, live (handed off from Home) or after the fact, through [LogWizardState].
 * Fetches weather for the wizard and logs its [LogRequest] through [OutfitLogging].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LogOutfitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val outfitLogging: OutfitLogging,
    private val recommendations: Recommendations,
    catalogRepository: CatalogRepository,
    private val forecast: Forecast,
    private val liveOutfitHandoffStore: LiveOutfitHandoffStore,
    private val preferencesManager: PreferencesManager,
    geocodingApi: GeocodingApi,
    private val clock: Clock
) : ViewModel() {

    private val isLive: Boolean = savedStateHandle.get<Boolean>("isLive") ?: false

    private val _uiState = MutableStateFlow(LogOutfitUiState())
    val uiState: StateFlow<LogOutfitUiState> = _uiState.asStateFlow()

    private val placeSearcher = PlaceSearch(geocodingApi, viewModelScope)
    val placeSearch: StateFlow<PlaceSearchState> = placeSearcher.state

    val availableItems: StateFlow<List<ClothingItem>> = _uiState
        .map { it.activityType }
        .distinctUntilChanged()
        .flatMapLatest { catalogRepository.getItemsByActivityType(it) }
        .map { itemsToOffer(it, emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Likely items follow the picked hour, the activity type and the (reactive) apparent/real
        // temperature preference.
        viewModelScope.launch {
            combine(
                _uiState.map { it.wizard?.selectedHour to it.activityType }.distinctUntilChanged(),
                preferencesManager.useApparentTemperature
            ) { (hour, activityType), useApparent -> Triple(hour, activityType, useApparent) }
                .collectLatest { (hour, activityType, useApparent) ->
                    val ids = if (hour != null) {
                        recommendations.likelyItemIds(activityType, hour.referenceTemp(useApparent), useApparent)
                    } else emptySet()
                    _uiState.update { it.copy(likelyItemIds = ids) }
                }
        }
        viewModelScope.launch { start() }
    }

    private suspend fun start() {
        val now = clock.currentHour()
        val currentPlaceName = forecast.location?.name ?: ""
        if (isLive) {
            val handoff = liveOutfitHandoffStore.take()
            // The handoff's activity type, never the DataStore value Home writes asynchronously.
            val activityType = handoff?.activityType ?: preferencesManager.selectedActivityType.first()
            val loaded = loadHours(LogWizardState.liveStartDate(now, handoff), null)
            _uiState.update {
                it.copy(activityType = activityType, wizard = LogWizardState.live(now, handoff, loaded, currentPlaceName))
            }
        } else {
            val activityType = preferencesManager.selectedActivityType.first()
            placeSearcher.setRecentPlaces(
                recentPlaces(outfitLogging.allEntries().first().map { it.weatherSnapshot }, forecast.location)
            )
            val loaded = loadHours(now.toLocalDate(), null)
            _uiState.update {
                it.copy(activityType = activityType, wizard = LogWizardState.past(now, loaded, currentPlaceName))
            }
        }
    }

    /** Hours of [date] at [at] (null = the current location), with that place; null if none could be fetched. */
    private suspend fun loadHours(date: LocalDate, at: Place?): PlacedHours? =
        try {
            if (at == null) forecast.hoursFor(date) else PlacedHours(at, forecast.hoursAt(at.lat, at.lon, date))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun updateWizard(change: (LogWizardState) -> LogWizardState) =
        _uiState.update { s -> s.wizard?.let { s.copy(wizard = change(it)) } ?: s }

    /** Applies [change]; if it asked for new hours, fetches them and hands them back to the wizard. */
    private fun updateAndFetch(change: (LogWizardState) -> LogWizardState) {
        val before = _uiState.value.wizard ?: return
        updateWizard(change)
        val after = _uiState.value.wizard ?: return
        if (after === before || !after.loadingHours) return
        val date = after.date
        val place = after.place
        viewModelScope.launch {
            val loaded = loadHours(date, place)
            updateWizard { it.hoursLoaded(date, place, loaded) }
        }
    }

    fun toggleItem(itemId: Long) = updateWizard { it.toggleItem(itemId) }

    fun setRating(rating: ComfortRating?) = updateWizard { it.rate(rating) }

    fun setNotes(text: String) = updateWizard { it.setNotes(text) }

    fun pickClockHour(hour: Int) = updateWizard { it.pickClockHour(hour) }

    fun pickDate(date: LocalDate) = updateAndFetch { it.pickDate(date) }

    /** Logs at [place] (null = the current location) and closes the place sheet. */
    fun pickPlace(place: Place?) {
        placeSearcher.close()
        updateAndFetch { it.pickPlace(place) }
    }

    fun openPlaceSheet() = placeSearcher.open()

    fun closePlaceSheet() = placeSearcher.close()

    fun setPlaceQuery(text: String) = placeSearcher.setQuery(text)

    /** Next: moves on, or on the last step logs the outfit. */
    fun next() {
        val state = _uiState.value
        if (state.saveState == SaveState.SAVING || state.saveState == SaveState.SAVED) return
        val wizard = state.wizard ?: return
        when (val move = wizard.next()) {
            is WizardMove.To -> updateWizard { move.state }
            is WizardMove.Save -> save(move.request)
            WizardMove.Leave -> Unit
        }
    }

    /** Back: moves back a step; true when the wizard asks to leave instead (first step). */
    fun back(): Boolean {
        val wizard = _uiState.value.wizard ?: return true
        return when (val move = wizard.back()) {
            is WizardMove.To -> { updateWizard { move.state }; false }
            WizardMove.Leave -> true
            is WizardMove.Save -> false
        }
    }

    /** Called once the screen has shown a [SaveState.FAILED] save; Next can try again. */
    fun onSaveFailureShown() {
        _uiState.update { if (it.saveState == SaveState.FAILED) it.copy(saveState = SaveState.IDLE) else it }
    }

    private fun save(request: LogRequest) {
        val activityType = _uiState.value.activityType
        _uiState.update { it.copy(saveState = SaveState.SAVING) }
        viewModelScope.launch {
            val saved = try {
                outfitLogging.log(
                    hour = request.hour,
                    location = request.place,
                    activityType = activityType,
                    clothingItemIds = request.itemIds,
                    rating = request.rating,
                    notes = request.notes,
                    workoutDurationHours = request.durationHours,
                    mode = request.mode
                )
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (saved && request.mode == LogMode.LIVE) liveOutfitHandoffStore.reportSaved()
            _uiState.update { it.copy(saveState = if (saved) SaveState.SAVED else SaveState.FAILED) }
        }
    }
}
