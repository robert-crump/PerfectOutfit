package com.example.perfectoutfit.feature.settings

import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.exportimport.CURRENT_EXPORT_VERSION
import com.example.perfectoutfit.core.exportimport.ExportImportManager
import com.example.perfectoutfit.core.location.LocationPermissions
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.feature.widget.FakeWidgetUpdater
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import com.example.perfectoutfit.testutil.outfitLoggingOver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val hat = ClothingItem(id = 1, bodyPart = BodyPart.HEAD_THROAT, name = "Hat", isDefault = true)
    private val scarf = ClothingItem(id = 2, bodyPart = BodyPart.HEAD_THROAT, name = "Scarf", isDefault = false)

    private val clothingItemDao = FakeClothingItemDao()
    private val entryDao = FakeOutfitEntryDao()
    private val itemDao = FakeOutfitItemDao()
    private val snapshotDao = FakeWeatherSnapshotDao()
    private val widget = FakeWidgetUpdater()
    private val files = FakeDocumentFiles()

    private val manager = ExportImportManager(
        clothingItemDao, snapshotDao, entryDao, itemDao,
        outfitLoggingOver(entryDao, itemDao, snapshotDao),
        json
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { clothingItemDao.insertAll(listOf(hat)) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(documentFiles: DocumentFiles = files) = SettingsViewModel(
        exportImportManager = manager,
        documentFiles = documentFiles,
        preferencesManager = PreferencesManager(FakePreferencesDataStore()),
        widgetUpdater = widget,
        locationPermissions = object : LocationPermissions {
            override fun hasForeground() = true
            override fun hasBackground() = false
        },
        recommendations = Recommendations(entryDao, clothingItemDao)
    )

    /** A file another device exported, holding only [scarf]. */
    private fun otherDevicesExport(): String = runBlocking {
        val otherItems = FakeClothingItemDao().apply { insertAll(listOf(scarf)) }
        ExportImportManager(
            otherItems, FakeWeatherSnapshotDao(), FakeOutfitEntryDao(), FakeOutfitItemDao(), outfitLoggingOver(), json
        ).exportToJson()
    }

    @Test
    fun `export writes all data to the picked document`() {
        val viewModel = viewModel()

        viewModel.exportData("content://export")

        val written = files.documents.getValue("content://export")
        assertTrue(written.contains("\"version\":$CURRENT_EXPORT_VERSION"))
        assertTrue(written.contains("\"name\":\"Hat\""))
        assertEquals("Data exported successfully", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `a failed write reports the export as failed`() {
        val failing = object : DocumentFiles by files {
            override suspend fun writeText(uri: String, text: String) = throw IllegalStateException("Could not write file")
        }
        val viewModel = viewModel(failing)

        viewModel.exportData("content://export")

        assertEquals("Export failed: Could not write file", viewModel.uiState.value.message)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `import replaces all data with the picked document and redraws the widget`() {
        files.documents["content://import"] = otherDevicesExport()
        val viewModel = viewModel()

        viewModel.importData("content://import")

        assertEquals(listOf(scarf), runBlocking { clothingItemDao.getAll() })
        assertEquals("Data imported successfully", viewModel.uiState.value.message)
        assertEquals(1, widget.redraws)
    }

    @Test
    fun `an unreadable document fails the import and leaves data untouched`() {
        val viewModel = viewModel()

        viewModel.importData("content://missing")

        assertEquals("Import failed: Could not read file", viewModel.uiState.value.message)
        assertEquals(listOf(hat), runBlocking { clothingItemDao.getAll() })
        assertEquals(0, widget.redraws)
    }

    @Test
    fun `a file from a newer app version is refused with an update hint`() {
        files.documents["content://newer"] = """{"version": ${CURRENT_EXPORT_VERSION + 1}}"""
        val viewModel = viewModel()

        viewModel.importData("content://newer")

        assertTrue(viewModel.uiState.value.message!!.contains("Update the app"))
        assertEquals(listOf(hat), runBlocking { clothingItemDao.getAll() })
    }
}
