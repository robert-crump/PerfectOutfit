package com.example.perfectoutfit.feature.catalog

import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ClothingItemActivityType
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
class CatalogViewModelTest {

    private val itemDao = FakeClothingItemDao()
    private val entryDao = FakeOutfitEntryDao()
    private lateinit var viewModel: CatalogViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CatalogViewModel(CatalogRepository(itemDao, entryDao, FakeDatabaseTransactionRunner()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.observeItems() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.clothingItems.collect {} }
    }

    private suspend fun seed(id: Long, name: String, vararg types: ActivityType) {
        itemDao.insert(ClothingItem(id = id, bodyPart = BodyPart.UPPER_BODY, name = name))
        itemDao.insertActivityTypes(types.map { ClothingItemActivityType(id, it) })
    }

    @Test
    fun `the add dialog pre-ticks the viewed activity type`() {
        viewModel.selectActivityType(ActivityType.RUNNING)

        viewModel.showAddDialog()

        assertEquals(CatalogDialogState.Add(null, setOf(ActivityType.RUNNING)), viewModel.dialogState.value)
    }

    @Test
    fun `an item added to two activity types is listed under both`() = runTest {
        observeItems()

        viewModel.addItem(BodyPart.UPPER_BODY, " Rain Jacket ", setOf(ActivityType.CYCLING, ActivityType.RUNNING))

        assertEquals(listOf("Rain Jacket"), viewModel.clothingItems.value.map { it.name })
        viewModel.selectActivityType(ActivityType.RUNNING)
        assertEquals(listOf("Rain Jacket"), viewModel.clothingItems.value.map { it.name })
    }

    @Test
    fun `adding needs at least one activity type`() = runTest {
        viewModel.addItem(BodyPart.UPPER_BODY, "Rain Jacket", emptySet())

        assertTrue(itemDao.getAll().isEmpty())
    }

    @Test
    fun `edit opens with the item's activity types and saves name and set`() = runTest {
        observeItems()
        seed(1, "Jersey", ActivityType.CYCLING)
        val jersey = viewModel.clothingItems.value.single()

        viewModel.showEditDialog(jersey)
        assertEquals(CatalogDialogState.Edit(jersey, setOf(ActivityType.CYCLING)), viewModel.dialogState.value)

        viewModel.editItem(jersey, "Long Jersey", setOf(ActivityType.RUNNING))

        // Unticking the viewed activity type drops the item from this list.
        assertTrue(viewModel.clothingItems.value.isEmpty())
        assertEquals(
            listOf(ClothingItemActivityType(1, ActivityType.RUNNING)),
            itemDao.getAllActivityTypes()
        )
        assertEquals("Long Jersey", itemDao.getById(1)?.name)
        assertEquals(CatalogDialogState.Hidden, viewModel.dialogState.value)
    }

    @Test
    fun `delete confirmation names the other activity types and the affected entries`() = runTest {
        observeItems()
        seed(1, "Jersey", ActivityType.CYCLING, ActivityType.RUNNING)
        entryDao.entriesWithAnyItemCount = 37
        viewModel.enterSelectionMode(viewModel.clothingItems.value.single())

        viewModel.showDeleteConfirmDialog()

        val state = viewModel.dialogState.value as CatalogDialogState.DeleteConfirm
        assertEquals(listOf("Jersey"), state.itemNames)
        assertEquals(listOf(ActivityType.RUNNING), state.otherActivityTypes)
        assertEquals(37, state.affectedEntryCount)
    }

    @Test
    fun `delete removes the item from every activity type`() = runTest {
        observeItems()
        seed(1, "Jersey", ActivityType.CYCLING, ActivityType.RUNNING)
        viewModel.enterSelectionMode(viewModel.clothingItems.value.single())

        viewModel.deleteSelectedItems()

        assertTrue(itemDao.getAll().isEmpty())
        assertTrue(itemDao.getAllActivityTypes().isEmpty())
        assertTrue(viewModel.selectedItemIds.value.isEmpty())
    }
}
