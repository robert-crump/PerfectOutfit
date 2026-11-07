package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.Place
import com.example.perfectoutfit.core.network.GeocodingApi
import com.example.perfectoutfit.feature.outfit.RecentPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A city search hit: where it is, plus "Region, Country" to tell same-named towns apart. */
data class PlaceSearchResult(val location: Place, val region: String)

/** Typing pause before a city search runs. */
const val PLACE_SEARCH_DEBOUNCE_MS = 300L

/** Characters needed before a city search runs. */
const val PLACE_SEARCH_MIN_CHARS = 2

data class PlaceSearchState(
    val showSheet: Boolean = false,
    val recentPlaces: List<RecentPlace> = emptyList(),
    val query: String = "",
    val results: List<PlaceSearchResult> = emptyList(),
    val failed: Boolean = false
)

/**
 * The past-log place sheet: recent places plus a debounced city search. Picking a place is the
 * wizard's business; this only finds them.
 */
class PlaceSearch(private val geocodingApi: GeocodingApi, scope: CoroutineScope) {

    private val _state = MutableStateFlow(PlaceSearchState())
    val state: StateFlow<PlaceSearchState> = _state.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        scope.launch { query.collectLatest { search(it.trim()) } }
    }

    fun setRecentPlaces(places: List<RecentPlace>) = _state.update { it.copy(recentPlaces = places) }

    fun open() = _state.update { it.copy(showSheet = true) }

    /** Closes the sheet and forgets the typed query. */
    fun close() {
        query.value = ""
        _state.update { it.copy(showSheet = false, query = "") }
    }

    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        query.value = text
    }

    /** Runs a city search for [query] once typing pauses; cancelled by the next keystroke. */
    private suspend fun search(query: String) {
        if (query.length < PLACE_SEARCH_MIN_CHARS) {
            _state.update { it.copy(results = emptyList(), failed = false) }
            return
        }
        delay(PLACE_SEARCH_DEBOUNCE_MS)
        val results = try {
            geocodingApi.search(query).results.map { r ->
                PlaceSearchResult(
                    location = Place(r.latitude, r.longitude, r.name),
                    region = listOf(r.admin1, r.country).filter { it.isNotBlank() }.joinToString(", ")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        _state.update { it.copy(results = results.orEmpty(), failed = results == null) }
    }
}
