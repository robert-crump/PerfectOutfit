package com.example.perfectoutfit.feature.settings

import com.example.perfectoutfit.core.database.DatabaseTransactionRunner
import com.example.perfectoutfit.core.database.dao.ClothingItemDao
import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.database.dao.OutfitItemDao
import com.example.perfectoutfit.core.database.dao.WeatherSnapshotDao
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.WeatherSnapshot
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/** Bumped whenever the shape of [ExportData] changes in a way old files won't have. */
const val CURRENT_EXPORT_VERSION = 1

/**
 * The full export/import envelope. Each entity is serialized directly (no field-by-field
 * mirror DTO), so a field exists in exactly one place: the Room entity itself. The JSON
 * shape this produces is identical to the hand-written DTOs it replaces, so files written
 * by earlier app versions (which lack [version]) still decode via that field's default.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ExportData(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val version: Int = CURRENT_EXPORT_VERSION,
    val clothingItems: List<ClothingItem> = emptyList(),
    val weatherSnapshots: List<WeatherSnapshot> = emptyList(),
    val outfitEntries: List<OutfitEntry> = emptyList(),
    val outfitItems: List<OutfitItem> = emptyList()
)

/**
 * Thrown by [ExportImportManager.importFromJson] when the file was written by a newer app
 * version than this one. Typed so the UI (and the Drive snapshot picker) can react to it.
 */
class UnsupportedExportVersionException(val fileVersion: Int) : Exception(
    "This backup was created by a newer version of the app (export version $fileVersion, " +
        "this app supports up to $CURRENT_EXPORT_VERSION). Update the app to restore this backup."
)

@Singleton
class ExportImportManager @Inject constructor(
    private val clothingItemDao: ClothingItemDao,
    private val weatherSnapshotDao: WeatherSnapshotDao,
    private val outfitEntryDao: OutfitEntryDao,
    private val outfitItemDao: OutfitItemDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val json: Json
) {
    suspend fun exportToJson(): String {
        val data = ExportData(
            clothingItems = clothingItemDao.getAll(),
            weatherSnapshots = weatherSnapshotDao.getAll(),
            outfitEntries = outfitEntryDao.getAll(),
            outfitItems = outfitItemDao.getAll()
        )
        return json.encodeToString(ExportData.serializer(), data)
    }

    /**
     * The envelope's `version`, or null for files written before versioning. Throws
     * [kotlinx.serialization.SerializationException] if [jsonString] is not valid JSON.
     */
    fun fileVersion(jsonString: String): Int? =
        (json.parseToJsonElement(jsonString) as? JsonObject)?.get("version")?.jsonPrimitive?.intOrNull

    /**
     * Rejects files newer than [CURRENT_EXPORT_VERSION] with [UnsupportedExportVersionException]
     * (checked before decoding, since a newer shape may not decode here); older or missing
     * versions decode via field defaults. Decodes and validates the whole file before
     * touching the database, so a malformed
     * file (bad JSON, wrong types, unknown enum constants) leaves existing data untouched.
     * The delete-then-insert itself runs in one transaction so a failure partway through
     * can't leave the database partially emptied either.
     */
    suspend fun importFromJson(jsonString: String) {
        val version = fileVersion(jsonString)
        if (version != null && version > CURRENT_EXPORT_VERSION) {
            throw UnsupportedExportVersionException(version)
        }
        val data = json.decodeFromString(ExportData.serializer(), jsonString)

        transactionRunner.runInTransaction {
            outfitItemDao.deleteAll()
            outfitEntryDao.deleteAll()
            weatherSnapshotDao.deleteAll()
            clothingItemDao.deleteAll()

            clothingItemDao.insertAll(data.clothingItems)
            data.weatherSnapshots.forEach { weatherSnapshotDao.insert(it) }
            data.outfitEntries.forEach { outfitEntryDao.insert(it) }
            outfitItemDao.insertAll(data.outfitItems)
        }
    }
}
