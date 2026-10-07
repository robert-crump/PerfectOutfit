package com.example.perfectoutfit.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/** A History row: the entry plus its reference temperature, rounded, as Home and Explorer show it. */
data class HistoryItem(
    val details: OutfitEntryWithDetails,
    val temperatureCelsius: Int
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val outfitLogging: OutfitLogging,
    preferencesManager: PreferencesManager
) : ViewModel() {

    private val _filterSport = MutableStateFlow<Sport?>(Sport.CYCLING)
    val filterSport: StateFlow<Sport?> = _filterSport

    // Null means "not loaded yet" so the UI can avoid flashing the empty-state
    // message before the first database emission arrives.
    val entries: StateFlow<List<HistoryItem>?> = _filterSport
        .flatMapLatest { sport ->
            if (sport != null) outfitLogging.entriesBySport(sport)
            else outfitLogging.allEntries()
        }
        .combine(preferencesManager.useApparentTemperature) { entries, useApparent ->
            entries.map { HistoryItem(it, it.weatherSnapshot.referenceTemp(useApparent).roundToInt()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Selection mode is on exactly while this is non-empty.
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

    /** Clears the selection too, so entries the new filter hides can't be deleted unseen. */
    fun setFilter(sport: Sport?) {
        _filterSport.value = sport
        clearSelection()
    }

    fun toggleSelection(entryId: Long) {
        _selectedIds.update { if (entryId in it) it - entryId else it + entryId }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        clearSelection()
        viewModelScope.launch { outfitLogging.delete(ids) }
    }
}
