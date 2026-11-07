package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.home.LiveOutfitHandoffStore
import com.example.perfectoutfit.feature.home.LiveOutfitPayload
import com.example.perfectoutfit.feature.outfit.LogMode
import com.example.perfectoutfit.testutil.FakeOpenMeteoApi
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
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
        // Stored activity type is the default (CYCLING).
        val vm = logOutfitViewModel(forecast, clock, isLive = true, entryDao = entryDao, handoff = handoff)

        assertEquals(LogStep.ITEMS, vm.uiState.value.wizard?.step)
        vm.next() // to the summary
        vm.next() // finish

        assertEquals(ActivityType.RUNNING, vm.uiState.value.activityType)
        assertEquals(listOf(ActivityType.RUNNING), entryDao.getAll().map { it.activityType })
        assertEquals(true, vm.uiState.value.isSaved)
        assertEquals(LogMode.LIVE, vm.uiState.value.wizard?.mode)
    }
}
