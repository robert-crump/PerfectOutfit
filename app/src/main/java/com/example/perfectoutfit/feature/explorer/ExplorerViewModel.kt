package com.example.perfectoutfit.feature.explorer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.feature.home.OutfitRepository
import com.example.perfectoutfit.feature.home.RecommendationPolicy
import com.example.perfectoutfit.feature.home.roundedTemp
import com.example.perfectoutfit.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExplorerUiState(
    val sport: Sport = Sport.CYCLING,
    val isLoading: Boolean = true,
    /** Sorted, distinct rounded temperatures that have rated outfit history for [sport]. */
    val stops: List<Int> = emptyList(),
    val selectedIndex: Int = 0,
    val recommendation: OutfitEntryWithDetails? = null
) {
    val selectedTemp: Int? get() = stops.getOrNull(selectedIndex)
}

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val forecastTemp: Int? = savedStateHandle.get<Int>("forecastTemp")
        ?.takeIf { it != Screen.Explorer.NO_FORECAST_TEMP }

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private var useApparent = true
    private var currentEntries: List<OutfitEntryWithDetails> = emptyList()

    init {
        viewModelScope.launch {
            useApparent = preferencesManager.useApparentTemperature.first()
            val sport = preferencesManager.selectedSport.first()
            loadStops(sport)
        }
    }

    fun selectSport(sport: Sport) {
        viewModelScope.launch { loadStops(sport) }
    }

    fun selectIndex(index: Int) {
        val stops = _uiState.value.stops
        if (index !in stops.indices) return
        _uiState.value = _uiState.value.copy(selectedIndex = index)
        applyRecommendationForCurrentStop()
    }

    private suspend fun loadStops(sport: Sport) {
        _uiState.value = _uiState.value.copy(isLoading = true, sport = sport, recommendation = null)
        val entries = outfitRepository.getRatedEntries(sport)
        currentEntries = entries
        val stops = entries.map { it.roundedTemp(useApparent) }.distinct().sorted()
        val initialIndex = when {
            stops.isEmpty() -> 0
            else -> nearestIndex(stops, forecastTemp ?: stops[stops.size / 2])
        }
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            stops = stops,
            selectedIndex = initialIndex
        )
        applyRecommendationForCurrentStop()
    }

    private fun nearestIndex(stops: List<Int>, target: Int): Int =
        stops.indices.minByOrNull { abs(stops[it] - target) } ?: 0

    private fun applyRecommendationForCurrentStop() {
        val temp = _uiState.value.selectedTemp ?: return
        val recommendation = RecommendationPolicy.findRecommendation(currentEntries, temp, useApparent)
        _uiState.value = _uiState.value.copy(recommendation = recommendation)
    }
}
