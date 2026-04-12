package com.example.perfectoutfit.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.feature.home.OutfitRepository
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
    private val outfitRepository: OutfitRepository
) : ViewModel() {

    private val _filterSport = MutableStateFlow<Sport?>(Sport.CYCLING)
    val filterSport: StateFlow<Sport?> = _filterSport

    val entries: StateFlow<List<OutfitEntryWithDetails>> = _filterSport
        .flatMapLatest { sport ->
            if (sport != null) outfitRepository.getEntriesBySport(sport)
            else outfitRepository.getAllEntriesWithDetails()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _lastDeletedEntry = MutableStateFlow<OutfitEntryWithDetails?>(null)
    val lastDeletedEntry: StateFlow<OutfitEntryWithDetails?> = _lastDeletedEntry.asStateFlow()

    fun setFilter(sport: Sport?) {
        _filterSport.value = sport
    }

    fun deleteEntry(entry: OutfitEntryWithDetails) {
        viewModelScope.launch {
            _lastDeletedEntry.value = entry
            outfitRepository.deleteEntry(entry.entry.id)
        }
    }

    fun undoDelete() {
        viewModelScope.launch {
            val deleted = _lastDeletedEntry.value ?: return@launch
            _lastDeletedEntry.value = null
            outfitRepository.restoreEntry(
                entry = deleted.entry,
                clothingItemIds = deleted.clothingItems.map { it.id }
            )
        }
    }

    fun clearLastDeleted() {
        _lastDeletedEntry.value = null
    }
}
