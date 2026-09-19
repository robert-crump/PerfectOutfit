package com.example.perfectoutfit.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val outfitLogging: OutfitLogging
) : ViewModel() {

    private val _filterSport = MutableStateFlow<Sport?>(Sport.CYCLING)
    val filterSport: StateFlow<Sport?> = _filterSport

    // Null means "not loaded yet" so the UI can avoid flashing the empty-state
    // message before the first database emission arrives.
    val entries: StateFlow<List<OutfitEntryWithDetails>?> = _filterSport
        .flatMapLatest { sport ->
            if (sport != null) outfitLogging.entriesBySport(sport)
            else outfitLogging.allEntries()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _lastDeletedEntry = MutableStateFlow<OutfitEntryWithDetails?>(null)
    val lastDeletedEntry: StateFlow<OutfitEntryWithDetails?> = _lastDeletedEntry.asStateFlow()

    fun setFilter(sport: Sport?) {
        _filterSport.value = sport
    }

    fun deleteEntry(entry: OutfitEntryWithDetails) {
        viewModelScope.launch {
            _lastDeletedEntry.value = entry
            outfitLogging.delete(entry.entry.id)
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            val deleted = _lastDeletedEntry.value ?: return@launch
            _lastDeletedEntry.value = null
            outfitLogging.restore(
                entry = deleted.entry,
                clothingItemIds = deleted.clothingItems.map { it.id }
            )
        }
    }

    fun clearLastDeleted() {
        _lastDeletedEntry.value = null
    }
}
