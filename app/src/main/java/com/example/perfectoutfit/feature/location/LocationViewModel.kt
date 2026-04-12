package com.example.perfectoutfit.feature.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.model.FavoriteLocation
import com.example.perfectoutfit.core.network.GeocodingResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LocationUiState(
    val searchQuery: String = "",
    val searchResults: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false
)

@HiltViewModel
class LocationViewModel @Inject constructor(
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = _uiState.asStateFlow()

    val favoriteLocations: StateFlow<List<FavoriteLocation>> =
        locationRepository.favoriteLocations
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    fun updateSearchQuery(query: String) {
        val capitalized = query.replaceFirstChar { it.uppercase() }
        _uiState.value = _uiState.value.copy(searchQuery = capitalized)
        searchJob?.cancel()
        if (capitalized.length < 2) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.value = _uiState.value.copy(isSearching = true)
            val results = locationRepository.searchCities(capitalized)
            _uiState.value = _uiState.value.copy(searchResults = results, isSearching = false)
        }
    }

    fun addFavorite(result: GeocodingResult) {
        viewModelScope.launch {
            locationRepository.addFavorite(result.name, result.latitude, result.longitude)
            _uiState.value = _uiState.value.copy(searchQuery = "", searchResults = emptyList())
        }
    }

    fun removeFavorite(id: Long) {
        viewModelScope.launch {
            locationRepository.removeFavorite(id)
        }
    }
}
