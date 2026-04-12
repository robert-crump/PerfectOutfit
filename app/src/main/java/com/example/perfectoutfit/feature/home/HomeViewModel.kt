package com.example.perfectoutfit.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.FavoriteLocation
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.notification.NotificationHelper
import com.example.perfectoutfit.feature.location.LocationRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import javax.inject.Inject

enum class WorkoutTab { COLDEST, WARMEST }

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadingMessage: String = "Loading...",
    /** Hours shown in the home screen (current hour → +24 h). */
    val hourlyWeather: List<HourlyWeather> = emptyList(),
    /** Index within hourlyWeather that the user has selected. */
    val selectedHourIndex: Int = 0,
    val warnings: List<String> = emptyList(),
    val selectedSport: Sport = Sport.CYCLING,
    val selectedLocationName: String = "Current Location",
    val favoriteLocations: List<FavoriteLocation> = emptyList(),
    val recommendation: OutfitEntryWithDetails? = null,
    /** Temperature used for recommendation lookup (apparent or real, user-adjustable). */
    val adjustedApparentTemp: Int = 0,
    val useApparentTemperature: Boolean = true,
    val error: String? = null,
    val currentCityName: String? = null,
    val workoutDurationHours: Int = 1,
    val activeWorkoutTab: WorkoutTab = WorkoutTab.COLDEST,
    val coldestHourIndex: Int = 0,
    val warmestHourIndex: Int = 0,
    val coldestRecommendation: OutfitEntryWithDetails? = null,
    val warmestRecommendation: OutfitEntryWithDetails? = null,
) {
    val selectedHour: HourlyWeather? get() = hourlyWeather.getOrNull(selectedHourIndex)

    val activeHourIndex: Int get() = when {
        workoutDurationHours <= 1 -> 0
        activeWorkoutTab == WorkoutTab.COLDEST -> coldestHourIndex
        else -> warmestHourIndex
    }
    val activeHour: HourlyWeather? get() = hourlyWeather.getOrNull(activeHourIndex)
    val activeRecommendation: OutfitEntryWithDetails? get() = when {
        workoutDurationHours <= 1 -> recommendation
        activeWorkoutTab == WorkoutTab.COLDEST -> coldestRecommendation
        else -> warmestRecommendation
    }
    val activeDisplayTemp: Int get() = activeHour?.let {
        if (useApparentTemperature) it.apparentTemperatureCelsius.roundToInt()
        else it.temperatureCelsius.roundToInt()
    } ?: adjustedApparentTemp
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val weatherRepository: WeatherRepository,
    private val outfitRepository: OutfitRepository,
    private val locationRepository: LocationRepository,
    private val preferencesManager: PreferencesManager,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var locationCancellationSource: CancellationTokenSource? = null

    init {
        viewModelScope.launch {
            combine(
                preferencesManager.selectedSport,
                preferencesManager.selectedLocationId,
                locationRepository.favoriteLocations
            ) { sport, locationId, favorites ->
                Triple(sport, locationId, favorites)
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null).collect { triple ->
                if (triple == null) return@collect
                val (sport, locationId, favorites) = triple
                _uiState.value = _uiState.value.copy(
                    selectedSport = sport,
                    favoriteLocations = favorites
                )
                if (locationId != null) {
                    val location = locationRepository.getFavoriteById(locationId)
                    if (location != null) {
                        _uiState.value = _uiState.value.copy(selectedLocationName = location.name)
                        fetchWeather(location.latitude, location.longitude)
                        fetchDeviceCity()
                    } else {
                        fetchCurrentLocationWeather()
                    }
                } else {
                    fetchCurrentLocationWeather()
                }
            }
        }
        viewModelScope.launch {
            preferencesManager.useApparentTemperature.collect { useApparent ->
                val selectedHour = _uiState.value.selectedHour
                val newTemp = if (useApparent)
                    selectedHour?.apparentTemperatureCelsius?.roundToInt() ?: _uiState.value.adjustedApparentTemp
                else
                    selectedHour?.temperatureCelsius?.roundToInt() ?: _uiState.value.adjustedApparentTemp
                _uiState.value = _uiState.value.copy(
                    useApparentTemperature = useApparent,
                    adjustedApparentTemp = newTemp
                )
                refreshRecommendation()
            }
        }
    }

    fun selectSport(sport: Sport, workoutDurationHours: Int = 1) {
        _uiState.value = _uiState.value.copy(
            selectedSport = sport,
            workoutDurationHours = workoutDurationHours,
            activeWorkoutTab = WorkoutTab.COLDEST,
            recommendation = null,
            coldestRecommendation = null,
            warmestRecommendation = null,
            coldestHourIndex = -1,
            warmestHourIndex = -1
        )
        viewModelScope.launch {
            preferencesManager.setSelectedSport(sport)
            refreshRecommendation()
        }
    }

    fun selectWorkoutTab(tab: WorkoutTab) {
        _uiState.value = _uiState.value.copy(activeWorkoutTab = tab)
    }

    fun selectHour(index: Int) {
        val hour = _uiState.value.hourlyWeather.getOrNull(index)
        val useApparent = _uiState.value.useApparentTemperature
        val newTemp = (if (useApparent) hour?.apparentTemperatureCelsius else hour?.temperatureCelsius)
            ?.roundToInt() ?: _uiState.value.adjustedApparentTemp
        _uiState.value = _uiState.value.copy(
            selectedHourIndex = index,
            adjustedApparentTemp = newTemp
        )
        viewModelScope.launch { refreshRecommendation() }
    }

    fun setAdjustedTemp(temp: Int) {
        _uiState.value = _uiState.value.copy(adjustedApparentTemp = temp)
        viewModelScope.launch { refreshRecommendation() }
    }

    fun selectCurrentLocation() {
        _uiState.value = _uiState.value.copy(selectedLocationName = "Current location")
        viewModelScope.launch {
            preferencesManager.setSelectedLocationId(null)
            fetchCurrentLocationWeather()
        }
    }

    fun selectFavoriteLocation(location: FavoriteLocation) {
        locationCancellationSource?.cancel()
        locationCancellationSource = null
        _uiState.value = _uiState.value.copy(selectedLocationName = location.name)
        viewModelScope.launch {
            preferencesManager.setSelectedLocationId(location.id)
            fetchWeather(location.latitude, location.longitude)
        }
        fetchDeviceCity()
    }

    /** Accept recommendation: saves a new outfit entry and schedules a rating notification. */
    fun acceptRecommendation(recommendation: OutfitEntryWithDetails) {
        viewModelScope.launch {
            val weather = _uiState.value.activeHour ?: return@launch
            val snapshotId = weatherRepository.saveSnapshot(buildSnapshot(weather))
            val now = System.currentTimeMillis()
            val entryId = outfitRepository.createEntry(
                entry = OutfitEntry(
                    weatherSnapshotId = snapshotId,
                    sport = _uiState.value.selectedSport,
                    createdAt = now
                ),
                clothingItemIds = recommendation.clothingItems.map { it.id }
            )
            notificationHelper.showRatingNotification(
                outfitEntryId = entryId,
                sport = _uiState.value.selectedSport,
                dateMs = now,
                durationHours = _uiState.value.workoutDurationHours
            )
        }
    }

    /**
     * Prepares state for the Custom Outfit screen and signals navigation.
     * Stores pre-filled item IDs in the repository so the screen can pick them up.
     */
    fun prepareCustomOutfit() {
        weatherRepository.pendingNewOutfitItemIds = emptyList()
        weatherRepository.cachedSelectedHourTime = _uiState.value.activeHour?.time
        weatherRepository.cachedWorkoutDurationHours = _uiState.value.workoutDurationHours
    }

    fun prepareEditOutfit() {
        val itemIds = _uiState.value.activeRecommendation?.clothingItems?.map { it.id } ?: emptyList()
        weatherRepository.pendingNewOutfitItemIds = itemIds
        weatherRepository.cachedSelectedHourTime = _uiState.value.activeHour?.time
        weatherRepository.cachedWorkoutDurationHours = _uiState.value.workoutDurationHours
    }

    /** Save weather with no outfit (no recommendation case) and notify to rate. */
    fun saveWeatherForLater() {
        viewModelScope.launch {
            val weather = _uiState.value.selectedHour ?: return@launch
            val snapshotId = weatherRepository.saveSnapshot(buildSnapshot(weather))
            val now = System.currentTimeMillis()
            val entryId = outfitRepository.createEntry(
                entry = OutfitEntry(
                    weatherSnapshotId = snapshotId,
                    sport = _uiState.value.selectedSport,
                    createdAt = now
                ),
                clothingItemIds = emptyList()
            )
            notificationHelper.showRatingNotification(
                outfitEntryId = entryId,
                sport = _uiState.value.selectedSport,
                dateMs = now,
                durationHours = _uiState.value.workoutDurationHours
            )
        }
    }

    private fun fetchCurrentLocationWeather() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = "Location permission required. Please grant location access."
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            loadingMessage = "Determining location...",
            error = null
        )

        locationCancellationSource?.cancel()
        val cancellationSource = CancellationTokenSource()
        locationCancellationSource = cancellationSource

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // Try last known location first — instant cache hit, sufficient for city-level accuracy.
        fusedClient.lastLocation.addOnSuccessListener { lastLocation ->
            if (lastLocation != null) {
                viewModelScope.launch {
                    applyCurrentLocation(lastLocation.latitude, lastLocation.longitude)
                }
            } else {
                // No cached location; request a fresh one.
                fusedClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationSource.token
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        viewModelScope.launch {
                            applyCurrentLocation(location.latitude, location.longitude)
                        }
                    } else {
                        tryFallbackToFirstFavorite("Could not determine location. Use the dropdown to select a city.")
                    }
                }.addOnFailureListener {
                    tryFallbackToFirstFavorite("Location unavailable. Use the dropdown to select a city.")
                }
            }
        }.addOnFailureListener {
            // lastLocation failed; fall through to getCurrentLocation.
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationSource.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    viewModelScope.launch {
                        applyCurrentLocation(location.latitude, location.longitude)
                    }
                } else {
                    tryFallbackToFirstFavorite("Could not determine location. Use the dropdown to select a city.")
                }
            }.addOnFailureListener {
                tryFallbackToFirstFavorite("Location unavailable. Use the dropdown to select a city.")
            }
        }
    }

    private suspend fun applyCurrentLocation(lat: Double, lon: Double) {
        val cityName = reverseGeocode(lat, lon)
        val displayName = if (cityName.isNotEmpty()) "Current location ($cityName)" else "Current location"
        _uiState.value = _uiState.value.copy(
            selectedLocationName = displayName,
            currentCityName = cityName.ifEmpty { null }
        )
        fetchWeather(lat, lon, displayName)
    }

    private fun tryFallbackToFirstFavorite(errorMessage: String) {
        val favorites = _uiState.value.favoriteLocations
        if (favorites.isNotEmpty()) {
            val first = favorites.first()
            viewModelScope.launch {
                preferencesManager.setSelectedLocationId(first.id)
                _uiState.value = _uiState.value.copy(selectedLocationName = first.name)
                fetchWeather(first.latitude, first.longitude)
            }
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = errorMessage
            )
        }
    }

    private suspend fun fetchWeather(lat: Double, lon: Double, locationName: String? = null) {
        _uiState.value = _uiState.value.copy(
            isLoading = !_uiState.value.isRefreshing,
            loadingMessage = "Loading weather data...",
            error = null
        )

        try {
            val allHours = weatherRepository.fetchWeather(
                lat, lon, locationName ?: _uiState.value.selectedLocationName
            )
            val displayHours = WeatherMapper.extractDisplayedHours(allHours)
            val warnings = buildList {
                val nextFour = displayHours.take(4)
                if (WeatherMapper.hasRainWarning(nextFour)) add("High chance of rain (>50%)")
                if (WeatherMapper.hasUvWarning(nextFour)) add("UV index >= 4")
                if (WeatherMapper.hasWindWarning(nextFour)) add("Wind speed >= 20 km/h")
            }
            val useApparent = _uiState.value.useApparentTemperature
            val firstHourTemp = (if (useApparent)
                displayHours.firstOrNull()?.apparentTemperatureCelsius
            else
                displayHours.firstOrNull()?.temperatureCelsius)?.roundToInt() ?: 0
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                hourlyWeather = displayHours,
                selectedHourIndex = 0,
                adjustedApparentTemp = firstHourTemp,
                warnings = warnings,
                error = null
            )
            refreshRecommendation()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = "Failed to load weather: ${e.message}"
            )
        }
    }

    private suspend fun refreshRecommendation() {
        val sport = _uiState.value.selectedSport
        val useApparent = _uiState.value.useApparentTemperature
        val duration = _uiState.value.workoutDurationHours
        val hours = _uiState.value.hourlyWeather

        if (duration <= 1) {
            val temp = _uiState.value.adjustedApparentTemp.toDouble()
            val recommendation = outfitRepository.findRecommendation(sport, temp, useApparent)
            // Discard if sport or duration changed while the query was in flight
            if (_uiState.value.selectedSport == sport && _uiState.value.workoutDurationHours == duration) {
                _uiState.value = _uiState.value.copy(recommendation = recommendation)
            }
        } else {
            val (coldestIdx, warmestIdx) = computeExtremes(hours, duration, useApparent)
            fun tempAt(idx: Int): Double = hours.getOrNull(idx)?.let {
                if (useApparent) it.apparentTemperatureCelsius else it.temperatureCelsius
            } ?: _uiState.value.adjustedApparentTemp.toDouble()
            val coldRec = outfitRepository.findRecommendation(sport, tempAt(coldestIdx), useApparent)
            val warmRec = outfitRepository.findRecommendation(sport, tempAt(warmestIdx), useApparent)
            // Discard if sport or duration changed while the queries were in flight
            if (_uiState.value.selectedSport == sport && _uiState.value.workoutDurationHours == duration) {
                _uiState.value = _uiState.value.copy(
                    coldestHourIndex = coldestIdx,
                    warmestHourIndex = warmestIdx,
                    coldestRecommendation = coldRec,
                    warmestRecommendation = warmRec
                )
            }
        }
    }

    private fun computeExtremes(
        hours: List<HourlyWeather>,
        duration: Int,
        useApparent: Boolean
    ): Pair<Int, Int> {
        val window = hours.take(duration)
        var coldestIdx = 0
        var warmestIdx = 0
        window.forEachIndexed { i, h ->
            val temp = if (useApparent) h.apparentTemperatureCelsius else h.temperatureCelsius
            val cold = if (useApparent) window[coldestIdx].apparentTemperatureCelsius
                       else window[coldestIdx].temperatureCelsius
            val warm = if (useApparent) window[warmestIdx].apparentTemperatureCelsius
                       else window[warmestIdx].temperatureCelsius
            if (temp <= cold) coldestIdx = i  // <= so later hour wins tiebreak
            if (temp >= warm) warmestIdx = i  // >= so later hour wins tiebreak
        }
        return coldestIdx to warmestIdx
    }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lon: Double): String =
        withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(lat, lon, 1) { addresses ->
                            cont.resume(
                                addresses.firstOrNull()?.locality
                                    ?: addresses.firstOrNull()?.subAdminArea
                                    ?: addresses.firstOrNull()?.adminArea
                                    ?: ""
                            )
                        }
                    }
                } else {
                    geocoder.getFromLocation(lat, lon, 1)
                        ?.firstOrNull()?.locality ?: ""
                }
            } catch (e: Exception) {
                ""
            }
        }

    /** Called when the screen resumes; retries weather load if a permission error is showing. */
    fun onLocationPermissionMaybeGranted() {
        if (_uiState.value.error?.contains("permission", ignoreCase = true) == true) {
            refreshWeather()
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val lat = weatherRepository.cachedLat.takeIf { it != 0.0 }
            val lon = weatherRepository.cachedLon.takeIf { it != 0.0 }
            if (lat != null && lon != null) {
                fetchWeather(lat, lon)
            } else {
                fetchCurrentLocationWeather()
            }
        }
    }

    private fun fetchDeviceCity() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                viewModelScope.launch {
                    val city = reverseGeocode(loc.latitude, loc.longitude)
                    if (city.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(currentCityName = city)
                    }
                }
            }
        }
    }

    private fun buildSnapshot(weather: HourlyWeather): WeatherSnapshot = WeatherSnapshot(
        timestamp = weather.time.atZone(java.time.ZoneId.systemDefault()).toEpochSecond() * 1000,
        latitude = weatherRepository.cachedLat,
        longitude = weatherRepository.cachedLon,
        locationName = _uiState.value.selectedLocationName,
        temperatureCelsius = weather.temperatureCelsius,
        apparentTemperatureCelsius = weather.apparentTemperatureCelsius,
        windSpeedKmh = weather.windSpeedKmh,
        windDirectionDegrees = weather.windDirectionDegrees,
        uvIndex = weather.uvIndex,
        cloudCoverPercent = weather.cloudCoverPercent,
        precipitationProbabilityPercent = weather.precipitationProbabilityPercent
    )
}
