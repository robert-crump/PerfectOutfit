package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Place
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.network.GeocodingApi
import com.example.perfectoutfit.core.network.GeocodingResponse
import com.example.perfectoutfit.core.network.GeocodingResult
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.testutil.FakeOpenMeteoApi
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
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

/** The past-log place picker, through [LogOutfitViewModel]: weather fetching, saving, search. */
@OptIn(ExperimentalCoroutinesApi::class)
class LogLocationPickerTest {

    private val day = LocalDate.of(2026, 3, 10)
    private val clock = MutableClock(day.atTime(18, 0).toInstant(ZoneOffset.UTC))
    private val api = FakeOpenMeteoApi((0..23).map { day.atTime(it, 0) })
    private val forecast = Forecast(api, clock)
    private val entryDao = FakeOutfitEntryDao()
    private val snapshotDao = FakeWeatherSnapshotDao()
    private val dispatcher = UnconfinedTestDispatcher()

    private val cologne = Place(50.94, 6.96, "Cologne")

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

    private suspend fun viewModel(): LogOutfitViewModel {
        forecast.refresh(50.78, 6.08, "Aachen")
        return logOutfitViewModel(forecast, clock, entryDao = entryDao, snapshotDao = snapshotDao, geocodingApi = geocoding)
    }

    private val LogOutfitViewModel.wizard: LogWizardState get() = checkNotNull(uiState.value.wizard)

    @Test
    fun `past logging starts at the current location and offers recent places`() = runTest(dispatcher) {
        entryDao.details.value = listOf(
            OutfitEntryWithDetails(
                OutfitEntry(id = 1, weatherSnapshotId = 7, activityType = ActivityType.CYCLING, createdAt = 0),
                snapshot("Cologne", 50.94, 6.96),
                emptyList()
            )
        )

        val vm = viewModel()

        assertNull(vm.wizard.place)
        assertEquals("Aachen", vm.wizard.placeName)
        assertEquals(listOf(cologne), vm.placeSearch.value.recentPlaces.map { it.location })
    }

    @Test
    fun `the wizard's today and max date come from the injected Clock`() = runTest(dispatcher) {
        val vm = viewModel()
        assertEquals(day, vm.wizard.today)
        assertEquals(day, vm.wizard.maxPickableDate)
        assertEquals(18, vm.wizard.pickerDefaultHour)
    }

    @Test
    fun `choosing a place fetches its weather and keeps date and clock hour`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.pickClockHour(9)
        vm.openPlaceSheet()

        vm.pickPlace(cologne)

        assertEquals(cologne, vm.wizard.place)
        assertEquals("Cologne", vm.wizard.placeName)
        assertEquals(cologne.lat to cologne.lon, api.dateRangeCoordinates.last())
        assertEquals(day, vm.wizard.date)
        assertEquals(day.atTime(9, 0), vm.wizard.selectedHour?.time)
        assertFalse(vm.wizard.loadingHours)
        assertFalse(vm.placeSearch.value.showSheet)
    }

    @Test
    fun `choosing a place before picking a time leaves the time unpicked`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.pickPlace(cologne)

        assertNull(vm.wizard.selectedHour)
    }

    @Test
    fun `saving logs the entry at the chosen place`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.pickPlace(cologne)
        vm.pickClockHour(9)
        vm.next() // to items
        vm.toggleItem(1)
        vm.next() // to the summary
        vm.next() // to the rating

        vm.next() // save

        val saved = snapshotDao.getAll().single()
        assertEquals("Cologne", saved.locationName)
        assertEquals(50.94, saved.latitude, 0.0)
        assertEquals(6.96, saved.longitude, 0.0)
        assertTrue(vm.uiState.value.isSaved)
    }

    @Test
    fun `saving at the current location logs the forecast's place`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.pickClockHour(9)
        vm.next(); vm.toggleItem(1); vm.next(); vm.next(); vm.next()

        val saved = snapshotDao.getAll().single()
        assertEquals("Aachen", saved.locationName)
        assertEquals(50.78, saved.latitude, 0.0)
    }

    @Test
    fun `a forecast location change after the hours loaded does not move the saved place`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.pickClockHour(9)
        forecast.refresh(52.52, 13.40, "Berlin")

        vm.next(); vm.toggleItem(1); vm.next(); vm.next(); vm.next()

        val saved = snapshotDao.getAll().single()
        assertEquals("Aachen", saved.locationName)
        assertEquals(50.78, saved.latitude, 0.0)
        assertEquals(6.08, saved.longitude, 0.0)
    }

    @Test
    fun `choosing the current location again goes back to GPS`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.pickPlace(cologne)
        vm.pickClockHour(9)

        vm.pickPlace(null)

        assertNull(vm.wizard.place)
        assertEquals("Aachen", vm.wizard.placeName)
        assertEquals(day.atTime(9, 0), vm.wizard.selectedHour?.time)
    }

    @Test
    fun `picking a date loads its hours`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.pickDate(day.minusDays(1))
        assertEquals(day.minusDays(1), vm.wizard.date)
        assertFalse(vm.wizard.loadingHours)
    }

    @Test
    fun `back from the first step asks to leave`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.back())
        vm.pickClockHour(9)
        vm.next()
        assertFalse(vm.back())
        assertEquals(LogStep.WHEN_WHERE, vm.wizard.step)
    }

    @Test
    fun `search waits for typing to pause and needs two characters`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setPlaceQuery("C")
        advanceTimeBy(1_000)
        assertTrue(geocoding.queries.isEmpty())

        vm.setPlaceQuery("Co")
        vm.setPlaceQuery("Col")
        advanceTimeBy(200)
        assertTrue(geocoding.queries.isEmpty())
        advanceTimeBy(200)

        assertEquals(listOf("Col"), geocoding.queries)
        val result = vm.placeSearch.value.results.single()
        assertEquals(cologne, result.location)
        assertEquals("North Rhine-Westphalia, Germany", result.region)
    }

    @Test
    fun `a failed search reports it and keeps the chips`() = runTest(dispatcher) {
        val vm = viewModel()
        geocoding.fail = true

        vm.setPlaceQuery("Col")
        advanceTimeBy(400)

        assertTrue(vm.placeSearch.value.failed)
        assertTrue(vm.placeSearch.value.results.isEmpty())
    }
}
