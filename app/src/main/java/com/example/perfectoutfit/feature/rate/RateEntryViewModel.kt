package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/** The user-editable parts of an existing entry. */
data class OutfitEdits(val itemIds: Set<Long>, val comfortRating: ComfortRating?, val notes: String)

data class RateEntryUiState(
    val isLoading: Boolean = true,
    val weatherSnapshot: WeatherSnapshot? = null,
    /** The entry's workout hour (its `createdAt`). */
    val workoutTime: LocalDateTime? = null,
    /** Today by the injected Clock, for the date label. */
    val today: LocalDate = LocalDate.MIN,
    /** Which temperature leads the weather card (the user's preference). */
    val useApparentTemperature: Boolean = true,
    val edits: OutfitEdits = OutfitEdits(emptySet(), null, ""),
    /** The entry as loaded, to detect unsaved edits; null until loaded (or if it's gone). */
    val savedEdits: OutfitEdits? = null,
    val isSaved: Boolean = false
) {
    val hasUnsavedEdits: Boolean
        get() = savedEdits != null && savedEdits != edits
}

/** Rating (and editing) an outfit entry that was already logged. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RateEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val outfitLogging: OutfitLogging,
    catalogRepository: CatalogRepository,
    preferencesManager: PreferencesManager,
    private val clock: Clock
) : ViewModel() {

    private val entryId: Long = checkNotNull(savedStateHandle.get<Long>("outfitEntryId"))

    private val _uiState = MutableStateFlow(RateEntryUiState(today = LocalDate.now(clock)))
    val uiState: StateFlow<RateEntryUiState> = _uiState.asStateFlow()

    private val activityType = MutableStateFlow<ActivityType?>(null)
    private val entryItems = MutableStateFlow<List<ClothingItem>>(emptyList())

    /** The entry's activity type's items plus any item already on the entry. */
    val availableItems: StateFlow<List<ClothingItem>> = activityType
        .filterNotNull()
        .flatMapLatest { catalogRepository.getItemsByActivityType(it) }
        .combine(entryItems, ::itemsToOffer)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            preferencesManager.useApparentTemperature.collect { useApparent ->
                _uiState.update { it.copy(useApparentTemperature = useApparent) }
            }
        }
        viewModelScope.launch {
            val details = outfitLogging.getEntryWithDetails(entryId)
            if (details == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            activityType.value = details.entry.activityType
            entryItems.value = details.clothingItems
            val edits = OutfitEdits(
                itemIds = details.clothingItems.map { it.id }.toSet(),
                comfortRating = details.entry.rating,
                notes = details.entry.notes
            )
            _uiState.update {
                it.copy(
                    isLoading = false,
                    weatherSnapshot = details.weatherSnapshot,
                    workoutTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(details.entry.createdAt), clock.zone),
                    edits = edits,
                    savedEdits = edits
                )
            }
        }
    }

    private fun edit(change: (OutfitEdits) -> OutfitEdits) = _uiState.update { it.copy(edits = change(it.edits)) }

    fun toggleItem(itemId: Long) = edit {
        it.copy(itemIds = if (itemId in it.itemIds) it.itemIds - itemId else it.itemIds + itemId)
    }

    fun setRating(rating: ComfortRating?) = edit { it.copy(comfortRating = rating) }

    fun setNotes(text: String) = edit { it.copy(notes = text) }

    /** Writes items, notes and rating at once, then reports [RateEntryUiState.isSaved]. */
    fun save() {
        val edits = _uiState.value.edits
        viewModelScope.launch {
            outfitLogging.update(entryId, edits.itemIds, edits.notes, edits.comfortRating)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}
