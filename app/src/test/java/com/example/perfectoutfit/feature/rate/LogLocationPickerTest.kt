package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.network.GeocodingApi
import com.example.perfectoutfit.core.network.GeocodingResponse
import com.example.perfectoutfit.core.network.GeocodingResult
import com.example.perfectoutfit.core.notification.FakeRatingReminder
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.home.LiveOutfitHandoffStore
import com.example.perfectoutfit.feature.outfit.LogLocation
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.feature.widget.FakeWidgetUpdater
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOpenMeteoApi
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import com.example.perfectoutfit.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class LogLocationPickerTest {

    private val day = LocalDate.of(2026, 3, 10)
    private val clock = MutableClock(day.atTime(18, 0).toInstant(ZoneOffset.UTC))
    private val api = FakeOpenMeteoApi((0..23).map { day.atTime(it, 0) })
    private val forecast = Forecast(api, clock)
    private val entryDao = FakeOutfitEntryDao()
    private val snapshotDao = FakeWeatherSnapshotDao()
    private val dispatcher = UnconfinedTestDispatcher()

    private val cologne = LogLocation("Cologne", 50.94, 6.96)

    private val geocoding = object : GeocodingApi {
        val queries = mutableListOf<String>()
        var fail = false
        override suspend fun search(query: String, count: Int): GeocodingResponse {
            queries += query
            if (fail) throw IOException("offline")
            return GeocodingResponse(
                listOf(GeocodingResult(1, "Cologne", 50.94, 6.96, "Germany", "North Rhine-Westphalia"))
            )
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun snapshot(name: String, lat: Double, lon: Double) = WeatherSnapshot(
        id = 7, timestamp = 0, latitude = lat, longitude = lon, locationName = name,
        temperatureCelsius = 0.0, apparentTemperatureCelsius = 0.0, windSpeedKmh = 0.0,
        windDirectionDegrees = 0, uvIndex = 0, cloudCoverPercent = 0, precipitationProbabilityPercent = 0
    )

    private suspend fun viewModel(): RateOutfitViewModel {
        forecast.refresh(50.78, 6.08, "Aachen")
        val logging = OutfitLogging(
            entryDao, FakeOutfitItemDao(), snapshotDao, FakeDatabaseTransactionRunner(), FakeRatingReminder(),
            FakeWidgetUpdater()
        )
        return RateOutfitViewModel(
            savedStateHandle = SavedStateHandle(),
            outfitLogging = logging,
            recommendations = Recommendations(entryDao, FakeClothingItemDao()),
            catalogRepository = CatalogRepository(FakeClothingItemDao(), entryDao, FakeDatabaseTransactionRunner()),
            forecast = forecast,
            liveOutfitHandoffStore = LiveOutfitHandoffStore(),
            preferencesManager = PreferencesManager(FakePreferencesDataStore()),
            geocodingApi = geocoding
        )
    }

    @Test
    fun `past logging starts at the current location and offers recent places`() = runTest(dispatcher) {
        entryDao.details.value = listOf(
            OutfitEntryWithDetails(
                OutfitEntry(id = 1, weatherSnapshotId = 7, activityType = ActivityType.CYCLING, createdAt = 0),
                snapshot("Cologne", 50.94, 6.96),
                emptyList()
            )
        )

        val state = viewModel().uiState.value

        assertNull(state.chosenLocation)
        assertEquals("Aachen", state.logLocationName)
        assertEquals(listOf(cologne), state.recentPlaces.map { it.location })
    }

    @Test
    fun `choosing a place fetches its weather and keeps date and clock hour`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectHourByClockHour(9)

        vm.selectLocation(cologne)

        val state = vm.uiState.value
        assertEquals(cologne, state.chosenLocation)
        assertEquals("Cologne", state.logLocationName)
        assertEquals(cologne.lat to cologne.lon, api.dateRangeCoordinates.last())
        assertEquals(day, state.selectedDate)
        assertEquals(day.atTime(9, 0), state.selectedHour?.time)
        assertFalse(state.isLoadingLocationWeather)
        assertFalse(state.showLocationSheet)
    }

    @Test
    fun `choosing a place before picking a time leaves the time unpicked`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.selectLocation(cologne)

        assertNull(vm.uiState.value.selectedHour)
    }

    @Test
    fun `saving logs the entry at the chosen place`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectLocation(cologne)
        vm.selectHourByClockHour(9)
        vm.toggleItem(1)

        vm.save()

        val saved = snapshotDao.getAll().single()
        assertEquals("Cologne", saved.locationName)
        assertEquals(50.94, saved.latitude, 0.0)
        assertEquals(6.96, saved.longitude, 0.0)
    }

    @Test
    fun `choosing the current location again goes back to GPS`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.selectLocation(cologne)
        vm.selectHourByClockHour(9)

        vm.selectLocation(null)

        val state = vm.uiState.value
        assertNull(state.chosenLocation)
        assertEquals("Aachen", state.logLocationName)
        assertEquals(day.atTime(9, 0), state.selectedHour?.time)
    }

    @Test
    fun `search waits for typing to pause and needs two characters`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setLocationQuery("C")
        advanceTimeBy(1_000)
        assertTrue(geocoding.queries.isEmpty())

        vm.setLocationQuery("Co")
        vm.setLocationQuery("Col")
        advanceTimeBy(200)
        assertTrue(geocoding.queries.isEmpty())
        advanceTimeBy(200)

        assertEquals(listOf("Col"), geocoding.queries)
        val result = vm.uiState.value.searchResults.single()
        assertEquals(cologne, result.location)
        assertEquals("North Rhine-Westphalia, Germany", result.region)
    }

    @Test
    fun `a failed search reports it and keeps the chips`() = runTest(dispatcher) {
        val vm = viewModel()
        geocoding.fail = true

        vm.setLocationQuery("Col")
        advanceTimeBy(400)

        assertTrue(vm.uiState.value.searchFailed)
        assertTrue(vm.uiState.value.searchResults.isEmpty())
    }
}
