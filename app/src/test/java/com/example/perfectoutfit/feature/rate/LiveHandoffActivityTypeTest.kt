package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.network.GeocodingApi
import com.example.perfectoutfit.core.network.GeocodingResponse
import com.example.perfectoutfit.core.notification.FakeRatingReminder
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.home.LiveOutfitHandoffStore
import com.example.perfectoutfit.feature.home.LiveOutfitPayload
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class LiveHandoffActivityTypeTest {

    private val day = LocalDate.of(2026, 3, 10)
    private val clock = MutableClock(day.atTime(8, 0).toInstant(ZoneOffset.UTC))
    private val forecast = Forecast(FakeOpenMeteoApi((0..23).map { day.atTime(it, 0) }), clock)
    private val entryDao = FakeOutfitEntryDao()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `live log uses the handed-off activity type, not the stored one`() = runTest(dispatcher) {
        forecast.refresh(50.78, 6.08, "Aachen")
        val handoff = LiveOutfitHandoffStore()
        handoff.set(
            LiveOutfitPayload(
                selectedHourTime = day.atTime(9, 0),
                activityType = ActivityType.RUNNING,
                workoutDurationHours = 1,
                prefillItemIds = listOf(1L)
            )
        )
        val vm = RateOutfitViewModel(
            savedStateHandle = SavedStateHandle(mapOf("isLive" to true)),
            outfitLogging = OutfitLogging(
                entryDao, FakeOutfitItemDao(), FakeWeatherSnapshotDao(), FakeDatabaseTransactionRunner(),
                FakeRatingReminder(), FakeWidgetUpdater()
            ),
            recommendations = Recommendations(entryDao, FakeClothingItemDao()),
            catalogRepository = CatalogRepository(FakeClothingItemDao(), entryDao, FakeDatabaseTransactionRunner()),
            forecast = forecast,
            liveOutfitHandoffStore = handoff,
            // Stored preference is the default (CYCLING).
            preferencesManager = PreferencesManager(FakePreferencesDataStore()),
            geocodingApi = object : GeocodingApi {
                override suspend fun search(query: String, count: Int) = GeocodingResponse(emptyList())
            }
        )

        vm.save()

        assertEquals(ActivityType.RUNNING, vm.uiState.value.activityType)
        assertEquals(listOf(ActivityType.RUNNING), entryDao.getAll().map { it.activityType })
    }
}
