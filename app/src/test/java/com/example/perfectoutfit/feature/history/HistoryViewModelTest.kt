package com.example.perfectoutfit.feature.history

import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.notification.FakeRatingReminder
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val entryDao = FakeOutfitEntryDao()
    private val preferences = PreferencesManager(FakePreferencesDataStore())
    private lateinit var viewModel: HistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        entryDao.details.value = listOf(
            details(1L, Sport.CYCLING, temp = 12.4, feelsLike = 9.6),
            details(2L, Sport.CYCLING, temp = -3.0, feelsLike = -7.0),
            details(3L, Sport.RUNNING, temp = 20.0, feelsLike = 21.0)
        )
        val logging = OutfitLogging(
            entryDao, FakeOutfitItemDao(), FakeWeatherSnapshotDao(), FakeDatabaseTransactionRunner(), FakeRatingReminder()
        )
        viewModel = HistoryViewModel(logging, preferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `temperature follows the apparent-temperature setting`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.entries.collect {} }

        assertEquals(listOf(10, -7), viewModel.entries.value!!.map { it.temperatureCelsius })

        preferences.setUseApparentTemperature(false)

        assertEquals(listOf(12, -3), viewModel.entries.value!!.map { it.temperatureCelsius })
    }

    @Test
    fun `toggling selects and deselects, and deselecting the last leaves selection mode`() {
        viewModel.toggleSelection(1L)
        viewModel.toggleSelection(2L)
        assertEquals(setOf(1L, 2L), viewModel.selectedIds.value)

        viewModel.toggleSelection(1L)
        viewModel.toggleSelection(2L)
        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    @Test
    fun `clearSelection empties the selection`() {
        viewModel.toggleSelection(1L)

        viewModel.clearSelection()

        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    @Test
    fun `changing the sport filter clears the selection`() {
        viewModel.toggleSelection(1L)

        viewModel.setFilter(Sport.RUNNING)

        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    @Test
    fun `deleteSelected deletes only the selected entries and leaves selection mode`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.entries.collect {} }
        viewModel.setFilter(null)
        viewModel.toggleSelection(1L)
        viewModel.toggleSelection(3L)

        viewModel.deleteSelected()

        assertEquals(listOf(2L), viewModel.entries.value!!.map { it.details.entry.id })
        assertTrue(viewModel.selectedIds.value.isEmpty())
    }

    @Test
    fun `deleteSelected with nothing selected deletes nothing`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.entries.collect {} }

        viewModel.deleteSelected()

        assertEquals(listOf(1L, 2L), viewModel.entries.value!!.map { it.details.entry.id })
    }

    private fun details(id: Long, sport: Sport, temp: Double, feelsLike: Double) = OutfitEntryWithDetails(
        entry = OutfitEntry(id = id, weatherSnapshotId = id, sport = sport, createdAt = id),
        weatherSnapshot = WeatherSnapshot(
            id = id,
            timestamp = id,
            latitude = 50.8,
            longitude = 6.1,
            locationName = "Aachen",
            temperatureCelsius = temp,
            apparentTemperatureCelsius = feelsLike,
            windSpeedKmh = 10.0,
            windDirectionDegrees = 240,
            uvIndex = 2,
            cloudCoverPercent = 50,
            precipitationProbabilityPercent = 10
        ),
        clothingItems = emptyList()
    )
}
