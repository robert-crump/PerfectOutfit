package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.location.LocationResult
import com.example.perfectoutfit.core.location.LocationSource
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.notification.FakeRatingReminder
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.feature.widget.FakeWidgetUpdater
import com.example.perfectoutfit.feature.widget.WidgetForecastStore
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOpenMeteoApi
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import com.example.perfectoutfit.testutil.MutableClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val day = LocalDate.of(2026, 3, 10)
    private val clock = MutableClock(day.atTime(12, 50).toInstant(ZoneOffset.UTC))
    private val hours = (0..23).map { day.atTime(it, 0) }
    // Feels like 10 °C at 12:00, 2 °C at 13:00, 20 °C at 14:00, then flat 15 °C.
    private val api = FakeOpenMeteoApi(hours, apparentTemperatureOf = {
        when (it.hour) { 12 -> 10.0; 13 -> 2.0; 14 -> 20.0; else -> 15.0 }
    })
    private val reminder = FakeRatingReminder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(location: LocationResult) = viewModel { location }

    private fun viewModel(location: () -> LocationResult): HomeViewModel {
        val entryDao = FakeOutfitEntryDao()
        val logging = OutfitLogging(
            entryDao, FakeOutfitItemDao(), FakeWeatherSnapshotDao(), FakeDatabaseTransactionRunner(), reminder,
            FakeWidgetUpdater()
        )
        return HomeViewModel(
            locationSource = object : LocationSource {
                override suspend fun current() = location()
            },
            forecast = Forecast(api, clock),
            liveOutfitHandoffStore = LiveOutfitHandoffStore(),
            outfitLogging = logging,
            recommendations = Recommendations(entryDao, FakeClothingItemDao()),
            preferencesManager = PreferencesManager(FakePreferencesDataStore()),
            widgetForecastStore = WidgetForecastStore(FakePreferencesDataStore(), Json, clock),
            widgetUpdater = FakeWidgetUpdater()
        )
    }

    @Test
    fun `accepting saves a live entry and reports SAVED`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))

        viewModel.acceptRecommendation(recommendation)

        assertEquals(AcceptState.SAVED, viewModel.uiState.value.acceptState)
        assertEquals(1, reminder.showCalls.size)
    }

    @Test
    fun `accepting without a forecast reports FAILED and saves nothing`() {
        val viewModel = viewModel(LocationResult.PermissionDenied)

        viewModel.acceptRecommendation(recommendation)

        assertEquals(AcceptState.FAILED, viewModel.uiState.value.acceptState)
        assertTrue(reminder.showCalls.isEmpty())
    }

    @Test
    fun `a denied permission is a typed error that resuming retries once granted`() {
        var result: LocationResult = LocationResult.PermissionDenied
        val viewModel = viewModel { result }
        assertEquals(HomeError.PermissionDenied, viewModel.uiState.value.error)

        result = LocationResult.Found(50.8, 6.1, "Aachen")
        viewModel.onLocationPermissionMaybeGranted()

        assertNull(viewModel.uiState.value.error)
        assertEquals("Aachen", viewModel.uiState.value.selectedLocationName)
    }

    @Test
    fun `resuming does not retry an unrelated error`() {
        val viewModel = viewModel(LocationResult.Unavailable("No GPS"))

        viewModel.onLocationPermissionMaybeGranted()

        assertEquals(HomeError.LocationUnavailable("No GPS"), viewModel.uiState.value.error)
    }

    @Test
    fun `a cancelled weather request shows no error`() {
        val hold = CompletableDeferred<Unit>()
        api.holdForecast = hold
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        hold.completeExceptionally(kotlinx.coroutines.CancellationException("superseded"))

        viewModel.refreshWeather()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `a failed weather fetch is a typed error`() {
        api.failNext = true
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))

        assertEquals(HomeError.WeatherFailed("offline"), viewModel.uiState.value.error)
    }

    @Test
    fun `a handled outcome goes back to IDLE`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        viewModel.acceptRecommendation(recommendation)

        viewModel.onAcceptHandled()

        assertEquals(AcceptState.IDLE, viewModel.uiState.value.acceptState)
    }

    @Test
    fun `resuming in a later hour shows that hour without refetching a fresh forecast`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        clock.advance(Duration.ofMinutes(14))

        viewModel.catchUpWithClock()

        assertEquals(day.atTime(13, 0), viewModel.uiState.value.activeHour?.time)
        assertEquals(1, api.forecastCalls)
    }

    @Test
    fun `resuming within the same hour changes nothing`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        clock.advance(Duration.ofMinutes(5))

        viewModel.catchUpWithClock()

        assertEquals(day.atTime(12, 0), viewModel.uiState.value.activeHour?.time)
        assertEquals(1, api.forecastCalls)
    }

    @Test
    fun `resuming with a stale forecast fetches a new one`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        clock.advance(Forecast.STALE_AFTER)

        viewModel.catchUpWithClock()

        assertEquals(2, api.forecastCalls)
        assertEquals(day.atTime(13, 0), viewModel.uiState.value.activeHour?.time)
    }

    @Test
    fun `a stale forecast keeps the outfit hidden until the new fetch is in`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        clock.advance(Forecast.STALE_AFTER)
        val fetch = CompletableDeferred<Unit>()
        api.holdForecast = fetch

        viewModel.catchUpWithClock()
        assertTrue(viewModel.uiState.value.isPreparingOutfit)
        // A widget tap during the fetch works its outfit out from the older forecast.
        viewModel.selectActivityType(ActivityType.RUNNING)
        assertTrue(viewModel.uiState.value.isPreparingOutfit)

        fetch.complete(Unit)
        assertFalse(viewModel.uiState.value.isPreparingOutfit)
        assertEquals(day.atTime(13, 0), viewModel.uiState.value.activeHour?.time)
    }

    @Test
    fun `a failed fetch on resume falls back to the known forecast`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        clock.advance(Forecast.STALE_AFTER)
        api.failNext = true

        viewModel.catchUpWithClock()

        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isPreparingOutfit)
        assertEquals(day.atTime(13, 0), viewModel.uiState.value.activeHour?.time)
    }

    @Test
    fun `picking an activity shows the outfit once it is worked out`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))

        viewModel.selectActivityType(ActivityType.RUNNING)

        assertFalse(viewModel.uiState.value.isPreparingOutfit)
    }

    @Test
    fun `picking an activity in a later hour uses that hour`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        clock.advance(Duration.ofMinutes(14))

        viewModel.selectActivityType(ActivityType.RUNNING)

        assertEquals(day.atTime(13, 0), viewModel.uiState.value.activeHour?.time)
    }

    @Test
    fun `Explorer from the activity grid gets the current hour's temperature, not the long workout's`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        assertEquals(10, viewModel.uiState.value.currentDisplayTemp)

        viewModel.selectActivityType(ActivityType.RUNNING, workoutDurationHours = 3)
        viewModel.selectWorkoutTab(WorkoutTab.WARMEST)

        assertEquals(20, viewModel.uiState.value.activeDisplayTemp)
        assertEquals(10, viewModel.uiState.value.currentDisplayTemp)
    }

    @Test
    fun `Explorer has no temperature while no hours are known`() {
        val viewModel = viewModel(LocationResult.Unavailable("no fix"))

        assertNull(viewModel.uiState.value.currentDisplayTemp)
    }

    @Test
    fun `the result step dresses for the coldest or warmest hour of a long workout`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))

        viewModel.selectActivityType(ActivityType.RUNNING, workoutDurationHours = 3)
        assertEquals(day.atTime(13, 0), viewModel.uiState.value.activeHour?.time)
        assertEquals(2, viewModel.uiState.value.activeDisplayTemp)

        viewModel.selectWorkoutTab(WorkoutTab.WARMEST)
        assertEquals(day.atTime(14, 0), viewModel.uiState.value.activeHour?.time)
        assertEquals(20, viewModel.uiState.value.activeDisplayTemp)
    }

    @Test
    fun `a workout duration stays within the long-workout bounds`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))

        viewModel.selectActivityType(ActivityType.RUNNING, workoutDurationHours = 99)
        assertEquals(HomeViewModel.MAX_LONG_WORKOUT_HOURS, viewModel.uiState.value.workoutDurationHours)

        viewModel.selectActivityType(ActivityType.RUNNING, workoutDurationHours = 0)
        assertEquals(HomeViewModel.SINGLE_HOUR, viewModel.uiState.value.workoutDurationHours)

        assertEquals(2, HomeViewModel.DEFAULT_LONG_WORKOUT_HOURS)
        assertEquals(23, HomeViewModel.MAX_LONG_WORKOUT_HOURS)
    }

    private val recommendation = OutfitEntryWithDetails(
        entry = OutfitEntry(id = 1L, weatherSnapshotId = 1L, activityType = ActivityType.CYCLING, createdAt = 1L, comfortRating = 0),
        weatherSnapshot = WeatherSnapshot(
            id = 1L,
            timestamp = 1L,
            latitude = 50.8,
            longitude = 6.1,
            locationName = "Aachen",
            temperatureCelsius = 10.0,
            apparentTemperatureCelsius = 10.0,
            windSpeedKmh = 10.0,
            windDirectionDegrees = 240,
            uvIndex = 2,
            cloudCoverPercent = 50,
            precipitationProbabilityPercent = 10
        ),
        clothingItems = emptyList()
    )
}
