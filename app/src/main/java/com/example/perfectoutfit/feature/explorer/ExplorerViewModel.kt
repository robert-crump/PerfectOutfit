package com.example.perfectoutfit.feature.explorer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.feature.recommendation.DegreeCell
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.feature.recommendation.TemperatureChart
import com.example.perfectoutfit.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExplorerUiState(
    val activityType: ActivityType = ActivityType.CYCLING,
    val isLoading: Boolean = true,
    val useApparent: Boolean = true,
    val chart: TemperatureChart? = null,
    /** The degree marked on the chart, always within its axis; null = none. */
    val selectedTemp: Int? = null
) {
    val selectedCell: DegreeCell? get() = selectedTemp?.let { chart?.cellAt(it) }
}

@HiltViewModel
class ExplorerViewModel @Inject constructor(
    private val recommendations: Recommendations,
    private val preferencesManager: PreferencesManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val forecastTemp: Int? = savedStateHandle.get<Int>("forecastTemp")
        ?.takeIf { it != Screen.Explorer.NO_FORECAST_TEMP }

    private val _uiState = MutableStateFlow(ExplorerUiState(selectedTemp = forecastTemp))
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    /** Explorer always opens on Cycling, whatever Home has selected. */
    private val activityType = MutableStateFlow(ActivityType.CYCLING)

    init {
        viewModelScope.launch { observeChart() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeChart() {
        // The basis is Settings' alone; observed, so changing it there re-resolves the chart.
        combine(activityType, preferencesManager.useApparentTemperature, ::Pair)
            .distinctUntilChanged()
            .flatMapLatest { (type, apparent) ->
                _uiState.update { it.copy(isLoading = true, activityType = type, useApparent = apparent) }
                recommendations.temperatureChart(type, apparent).map { chart -> type to chart }
            }
            .collect { (type, chart) ->
                _uiState.update { state ->
                    if (state.activityType != type) return@update state
                    state.copy(
                        isLoading = false,
                        chart = chart,
                        // A selection the new axis doesn't reach is dropped, not moved.
                        selectedTemp = state.selectedTemp?.takeIf { chart.cellAt(it) != null }
                    )
                }
            }
    }

    fun selectActivityType(type: ActivityType) {
        if (type == activityType.value) return
        // A different activity type starts again from Home's forecast temperature.
        _uiState.update { it.copy(selectedTemp = forecastTemp, chart = null) }
        activityType.value = type
    }

    /** Marks [temp] on the chart (clamped to its axis); selecting the marked degree again clears it. */
    fun selectTemp(temp: Int, toggle: Boolean = false) {
        _uiState.update { state ->
            val min = state.chart?.minTemp ?: return@update state
            val max = state.chart.maxTemp ?: return@update state
            val clamped = temp.coerceIn(min, max)
            state.copy(selectedTemp = if (toggle && clamped == state.selectedTemp) null else clamped)
        }
    }
}
