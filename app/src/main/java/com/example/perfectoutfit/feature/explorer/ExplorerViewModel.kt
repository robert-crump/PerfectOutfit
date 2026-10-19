package com.example.perfectoutfit.feature.explorer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.feature.recommendation.Recommendations
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
    val activityType: ActivityType = ActivityType.CYCLING,
    val isLoading: Boolean = true,
    val useApparent: Boolean = true,
    /** Sorted, distinct rounded temperatures that have rated outfit history for [activityType]. */
    val stops: List<Int> = emptyList(),
    val selectedIndex: Int = 0,
    val recommendation: OutfitEntryWithDetails? = null
) {
    val selectedTemp: Int? get() = stops.getOrNull(selectedIndex)
}

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val recommendations: Recommendations,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val forecastTemp: Int? = savedStateHandle.get<Int>("forecastTemp")
        ?.takeIf { it != Screen.Explorer.NO_FORECAST_TEMP }

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val activityType = preferencesManager.selectedActivityType.first()
            var initial = true
            // Observed, not read once: toggling the preference re-resolves stops and recommendation.
            preferencesManager.useApparentTemperature.collect { useApparent ->
                if (initial) {
                    initial = false
                    _uiState.value = _uiState.value.copy(useApparent = useApparent)
                    loadStops(activityType, preferredTarget = forecastTemp)
                } else {
                    applyTemperatureMode(useApparent)
                }
            }
        }
    }

    fun selectActivityType(activityType: ActivityType) {
        viewModelScope.launch { loadStops(activityType, preferredTarget = forecastTemp) }
    }

    fun selectTemperatureMode(useApparent: Boolean) {
        viewModelScope.launch { applyTemperatureMode(useApparent) }
    }

    private suspend fun applyTemperatureMode(useApparent: Boolean) {
        if (useApparent == _uiState.value.useApparent) return
        val previousTemp = _uiState.value.selectedTemp
        _uiState.value = _uiState.value.copy(useApparent = useApparent)
        loadStops(_uiState.value.activityType, preferredTarget = previousTemp)
    }

    fun selectIndex(index: Int) {
        val stops = _uiState.value.stops
        if (index !in stops.indices) return
        _uiState.value = _uiState.value.copy(selectedIndex = index)
        applyRecommendationForCurrentStop()
    }

    private suspend fun loadStops(activityType: ActivityType, preferredTarget: Int?) {
        _uiState.value = _uiState.value.copy(isLoading = true, activityType = activityType, recommendation = null)
        val useApparent = _uiState.value.useApparent
        val stops = recommendations.stops(activityType, useApparent)
        val initialIndex = when {
            stops.isEmpty() -> 0
            else -> nearestIndex(stops, preferredTarget ?: stops[stops.size / 2])
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
        val activityType = _uiState.value.activityType
        val useApparent = _uiState.value.useApparent
        viewModelScope.launch {
            val recommendation = recommendations.find(activityType, temp.toDouble(), useApparent)
            // Discard if the activityType, mode or stop changed while the query was in flight.
            val s = _uiState.value
            if (s.activityType == activityType && s.useApparent == useApparent && s.selectedTemp == temp) {
                _uiState.value = s.copy(recommendation = recommendation)
            }
        }
    }
}
