package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.home.LiveOutfitHandoffStore
import com.example.perfectoutfit.feature.home.LiveOutfitPayload
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class LogOutfitSaveTest {

    private val day = LocalDate.of(2026, 3, 10)
    private val clock = MutableClock(day.atTime(8, 0).toInstant(ZoneOffset.UTC))
    private val forecast = Forecast(FakeOpenMeteoApi((0..23).map { day.atTime(it, 0) }), clock)
    private val entryDao = FakeOutfitEntryDao()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val handoff = LiveOutfitHandoffStore()

    private suspend fun liveViewModel(): LogOutfitViewModel {
        forecast.refresh(50.78, 6.08, "Aachen")
        handoff.set(LiveOutfitPayload(day.atTime(9, 0), ActivityType.RUNNING, 1, listOf(1L)))
        return logOutfitViewModel(forecast, clock, isLive = true, entryDao = entryDao, handoff = handoff)
    }

    @Test
    fun `pressing Use outfit twice logs the outfit once`() = runTest(dispatcher) {
        val vm = liveViewModel()

        vm.next()
        vm.next()

        assertEquals(1, entryDao.getAll().size)
        assertEquals(SaveState.SAVED, vm.uiState.value.saveState)
    }

    @Test
    fun `a failed save stays in the wizard and can be tried again`() = runTest(dispatcher) {
        val vm = liveViewModel()
        entryDao.failNextInsert = true

        vm.next()

        assertEquals(SaveState.FAILED, vm.uiState.value.saveState)
        assertEquals(LogStep.ITEMS, vm.uiState.value.wizard?.step)
        assertEquals(0, entryDao.getAll().size)

        vm.onSaveFailureShown()
        assertEquals(SaveState.IDLE, vm.uiState.value.saveState)

        vm.next()
        assertEquals(SaveState.SAVED, vm.uiState.value.saveState)
        assertEquals(1, entryDao.getAll().size)
    }

    @Test
    fun `a saved live log tells Home, a failed one does not`() = runTest(dispatcher) {
        val vm = liveViewModel()
        entryDao.failNextInsert = true

        vm.next()
        assertFalse(handoff.outfitSaved.value)

        vm.onSaveFailureShown()
        vm.next()
        assertTrue(handoff.outfitSaved.value)
    }
}
