package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import com.example.perfectoutfit.testutil.MutableClock
import com.example.perfectoutfit.testutil.outfitLoggingOver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class RateEntryViewModelTest {

    private val day = LocalDate.of(2026, 3, 10)
    private val clock = MutableClock(day.atTime(18, 0).toInstant(ZoneOffset.UTC))
    private val entryDao = FakeOutfitEntryDao()
    private val itemDao = FakeOutfitItemDao()
    private val dispatcher = UnconfinedTestDispatcher()

    private val jersey = ClothingItem(id = 1, bodyPart = BodyPart.UPPER_BODY, name = "Jersey")
    private val socks = ClothingItem(id = 2, bodyPart = BodyPart.FEET, name = "Socks")
    private val workoutMs = day.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val snapshot = WeatherSnapshot(
        id = 3, timestamp = workoutMs, latitude = 50.78, longitude = 6.08, locationName = "Aachen",
        temperatureCelsius = 4.0, apparentTemperatureCelsius = 1.0, windSpeedKmh = 10.0,
        windDirectionDegrees = 180, uvIndex = 1, cloudCoverPercent = 50, precipitationProbabilityPercent = 10
    )
    private val entry = OutfitEntry(
        id = 7, weatherSnapshotId = 3, activityType = ActivityType.RUNNING,
        comfortRating = ComfortRating.TOO_HOT.value, createdAt = workoutMs, notes = "Windy"
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun viewModel(entryId: Long = 7): RateEntryViewModel {
        entryDao.insert(entry)
        itemDao.insertAll(listOf(OutfitItem(7, 1), OutfitItem(7, 2)))
        entryDao.details.value = listOf(OutfitEntryWithDetails(entry, snapshot, listOf(jersey, socks)))
        return RateEntryViewModel(
            savedStateHandle = SavedStateHandle(mapOf("outfitEntryId" to entryId)),
            outfitLogging = outfitLoggingOver(entryDao = entryDao, itemDao = itemDao),
            catalogRepository = CatalogRepository(FakeClothingItemDao(), entryDao, FakeDatabaseTransactionRunner()),
            preferencesManager = PreferencesManager(FakePreferencesDataStore()),
            clock = clock
        )
    }

    @Test
    fun `loads the entry's weather, workout hour and edits`() = runTest(dispatcher) {
        val state = viewModel().uiState.value

        assertFalse(state.isLoading)
        assertEquals(snapshot, state.weatherSnapshot)
        assertEquals(day.atTime(9, 0), state.workoutTime)
        assertEquals(day, state.today)
        assertEquals(OutfitEdits(setOf(1L, 2L), ComfortRating.TOO_HOT, "Windy"), state.edits)
        assertFalse(state.hasUnsavedEdits)
    }

    @Test
    fun `changing items, rating or notes is an unsaved edit, undoing it is not`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.toggleItem(2)
        assertTrue(vm.uiState.value.hasUnsavedEdits)
        vm.toggleItem(2)
        assertFalse(vm.uiState.value.hasUnsavedEdits)

        vm.setRating(null)
        assertTrue(vm.uiState.value.hasUnsavedEdits)
        vm.setRating(ComfortRating.TOO_HOT)
        vm.setNotes("Windy!")
        assertTrue(vm.uiState.value.hasUnsavedEdits)
    }

    @Test
    fun `saving writes items, notes and rating`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.toggleItem(2)
        vm.setRating(ComfortRating.PERFECT)
        vm.setNotes("Calm")

        vm.save()

        assertTrue(vm.uiState.value.isSaved)
        assertEquals(listOf(1L), itemDao.getByEntryId(7).map { it.clothingItemId })
        val saved = entryDao.getById(7)!!
        assertEquals(ComfortRating.PERFECT.value, saved.comfortRating)
        assertEquals("Calm", saved.notes)
    }

    @Test
    fun `a missing entry stops loading with nothing to lose`() = runTest(dispatcher) {
        val state = viewModel(entryId = 99).uiState.value

        assertFalse(state.isLoading)
        assertNull(state.weatherSnapshot)
        assertFalse(state.hasUnsavedEdits)
    }
}
