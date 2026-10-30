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
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import com.example.perfectoutfit.testutil.MutableClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
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
    private val api = FakeOpenMeteoApi(hours)
    private val reminder = FakeRatingReminder()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(location: LocationResult): HomeViewModel {
        val entryDao = FakeOutfitEntryDao()
        val logging = OutfitLogging(
            entryDao, FakeOutfitItemDao(), FakeWeatherSnapshotDao(), FakeDatabaseTransactionRunner(), reminder,
            FakeWidgetUpdater()
        )
        return HomeViewModel(
            locationSource = object : LocationSource {
                override suspend fun current() = location
            },
            forecast = Forecast(api, clock),
            liveOutfitHandoffStore = LiveOutfitHandoffStore(),
            outfitLogging = logging,
            recommendations = Recommendations(entryDao),
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
    fun `picking an activity in a later hour uses that hour`() {
        val viewModel = viewModel(LocationResult.Found(50.8, 6.1, "Aachen"))
        clock.advance(Duration.ofMinutes(14))

        viewModel.selectActivityType(ActivityType.RUNNING)

        assertEquals(day.atTime(13, 0), viewModel.uiState.value.activeHour?.time)
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
