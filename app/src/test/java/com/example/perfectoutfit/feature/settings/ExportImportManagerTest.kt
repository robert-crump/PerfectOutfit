package com.example.perfectoutfit.feature.settings

import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ExportImportManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun managerFor(
        clothingItemDao: FakeClothingItemDao,
        weatherSnapshotDao: FakeWeatherSnapshotDao,
        outfitEntryDao: FakeOutfitEntryDao,
        outfitItemDao: FakeOutfitItemDao
    ) = ExportImportManager(
        clothingItemDao,
        weatherSnapshotDao,
        outfitEntryDao,
        outfitItemDao,
        FakeDatabaseTransactionRunner(),
        json
    )

    private val clothingItem = ClothingItem(
        id = 1, sport = Sport.CYCLING, bodyPart = BodyPart.HEAD_THROAT, name = "Mütze", isDefault = true
    )
    private val snapshot = WeatherSnapshot(
        id = 1, timestamp = 1_000L, latitude = 52.5, longitude = 13.4, locationName = "Berlin",
        temperatureCelsius = 10.0, apparentTemperatureCelsius = 8.0, windSpeedKmh = 15.0,
        windDirectionDegrees = 180, uvIndex = 3, cloudCoverPercent = 40, precipitationProbabilityPercent = 20
    )
    private val entry = OutfitEntry(
        id = 1, weatherSnapshotId = 1, sport = Sport.CYCLING, comfortRating = 0,
        createdAt = 2_000L, ratedAt = 2_500L, notes = "Good ride"
    )
    private val outfitItem = OutfitItem(outfitEntryId = 1, clothingItemId = 1)

    private suspend fun seededSource(): ExportImportManager {
        val clothingItemDao = FakeClothingItemDao().apply { insertAll(listOf(clothingItem)) }
        val weatherSnapshotDao = FakeWeatherSnapshotDao().apply { insert(snapshot) }
        val outfitEntryDao = FakeOutfitEntryDao().apply { insert(entry) }
        val outfitItemDao = FakeOutfitItemDao().apply { insertAll(listOf(outfitItem)) }
        return managerFor(clothingItemDao, weatherSnapshotDao, outfitEntryDao, outfitItemDao)
    }

    @Test
    fun `round trip preserves all four tables`() = runTest {
        val exported = seededSource().exportToJson()

        val targetClothingItemDao = FakeClothingItemDao()
        val targetWeatherSnapshotDao = FakeWeatherSnapshotDao()
        val targetOutfitEntryDao = FakeOutfitEntryDao()
        val targetOutfitItemDao = FakeOutfitItemDao()
        val target = managerFor(
            targetClothingItemDao, targetWeatherSnapshotDao, targetOutfitEntryDao, targetOutfitItemDao
        )

        target.importFromJson(exported)

        assertEquals(listOf(clothingItem), targetClothingItemDao.getAll())
        assertEquals(listOf(snapshot), targetWeatherSnapshotDao.getAll())
        assertEquals(listOf(entry), targetOutfitEntryDao.getAll())
        assertEquals(listOf(outfitItem), targetOutfitItemDao.getAll())
    }

    @Test
    fun `export carries the current schema version`() = runTest {
        val exported = seededSource().exportToJson()

        assertTrue(exported.contains("\"version\":$CURRENT_EXPORT_VERSION"))
    }

    @Test
    fun `malformed file leaves existing data untouched`() = runTest {
        val clothingItemDao = FakeClothingItemDao().apply { insertAll(listOf(clothingItem)) }
        val weatherSnapshotDao = FakeWeatherSnapshotDao().apply { insert(snapshot) }
        val outfitEntryDao = FakeOutfitEntryDao().apply { insert(entry) }
        val outfitItemDao = FakeOutfitItemDao().apply { insertAll(listOf(outfitItem)) }
        val manager = managerFor(clothingItemDao, weatherSnapshotDao, outfitEntryDao, outfitItemDao)

        try {
            manager.importFromJson("{ not valid json")
            fail("expected a SerializationException")
        } catch (e: SerializationException) {
            // expected
        }

        assertEquals(listOf(clothingItem), clothingItemDao.getAll())
        assertEquals(listOf(snapshot), weatherSnapshotDao.getAll())
        assertEquals(listOf(entry), outfitEntryDao.getAll())
        assertEquals(listOf(outfitItem), outfitItemDao.getAll())
    }

    @Test
    fun `file exported by the previous app version still imports`() = runTest {
        // Shape produced by the pre-#8 ExportImportManager: hand-written DTOs with string
        // enum fields and no "version" key.
        val legacyJson = """
            {
              "clothingItems": [
                {"id": 1, "sport": "CYCLING", "bodyPart": "HEAD_THROAT", "name": "Mütze", "isDefault": true}
              ],
              "weatherSnapshots": [
                {"id": 1, "timestamp": 1000, "latitude": 52.5, "longitude": 13.4, "locationName": "Berlin",
                 "temperatureCelsius": 10.0, "apparentTemperatureCelsius": 8.0, "windSpeedKmh": 15.0,
                 "windDirectionDegrees": 180, "uvIndex": 3, "cloudCoverPercent": 40,
                 "precipitationProbabilityPercent": 20}
              ],
              "outfitEntries": [
                {"id": 1, "weatherSnapshotId": 1, "sport": "CYCLING", "comfortRating": 0,
                 "createdAt": 2000, "ratedAt": 2500, "notes": "Good ride"}
              ],
              "outfitItems": [
                {"outfitEntryId": 1, "clothingItemId": 1}
              ]
            }
        """.trimIndent()

        val clothingItemDao = FakeClothingItemDao()
        val weatherSnapshotDao = FakeWeatherSnapshotDao()
        val outfitEntryDao = FakeOutfitEntryDao()
        val outfitItemDao = FakeOutfitItemDao()
        val manager = managerFor(clothingItemDao, weatherSnapshotDao, outfitEntryDao, outfitItemDao)

        manager.importFromJson(legacyJson)

        assertEquals(listOf(clothingItem), clothingItemDao.getAll())
        assertEquals(listOf(snapshot), weatherSnapshotDao.getAll())
        assertEquals(listOf(entry), outfitEntryDao.getAll())
        assertEquals(listOf(outfitItem), outfitItemDao.getAll())
    }
}
