package com.example.perfectoutfit.feature.settings

import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.FavoriteLocation
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.home.OutfitRepository
import com.example.perfectoutfit.feature.home.WeatherRepository
import com.example.perfectoutfit.feature.location.LocationRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExportData(
    val clothingItems: List<ExportClothingItem>,
    val weatherSnapshots: List<ExportWeatherSnapshot>,
    val outfitEntries: List<ExportOutfitEntry>,
    val outfitItems: List<ExportOutfitItem>,
    val favoriteLocations: List<ExportFavoriteLocation> = emptyList()
)

@Serializable
data class ExportClothingItem(
    val id: Long,
    val sport: String,
    val bodyPart: String,
    val name: String,
    val isDefault: Boolean
)

@Serializable
data class ExportWeatherSnapshot(
    val id: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Int,
    val uvIndex: Int,
    val cloudCoverPercent: Int,
    val precipitationProbabilityPercent: Int
)

@Serializable
data class ExportOutfitEntry(
    val id: Long,
    val weatherSnapshotId: Long,
    val sport: String,
    val comfortRating: Int? = null,
    val createdAt: Long,
    val ratedAt: Long? = null,
    val notes: String = ""
)

@Serializable
data class ExportOutfitItem(
    val outfitEntryId: Long,
    val clothingItemId: Long
)

@Serializable
data class ExportFavoriteLocation(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int
)

@Singleton
class ExportImportManager @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val outfitRepository: OutfitRepository,
    private val weatherRepository: WeatherRepository,
    private val locationRepository: LocationRepository,
    private val json: Json
) {
    suspend fun exportToJson(): String {
        val clothingItems = catalogRepository.getAllItems().map {
            ExportClothingItem(it.id, it.sport.name, it.bodyPart.name, it.name, it.isDefault)
        }
        val snapshots = weatherRepository.getAllSnapshots().map {
            ExportWeatherSnapshot(
                it.id, it.timestamp, it.latitude, it.longitude, it.locationName,
                it.temperatureCelsius, it.apparentTemperatureCelsius, it.windSpeedKmh,
                it.windDirectionDegrees, it.uvIndex, it.cloudCoverPercent,
                it.precipitationProbabilityPercent
            )
        }
        val entries = outfitRepository.getAllEntries().map {
            ExportOutfitEntry(it.id, it.weatherSnapshotId, it.sport.name, it.comfortRating, it.createdAt, it.ratedAt, it.notes)
        }
        val items = outfitRepository.getAllOutfitItems().map {
            ExportOutfitItem(it.outfitEntryId, it.clothingItemId)
        }
        val locations = locationRepository.getAllFavoritesSync().map {
            ExportFavoriteLocation(it.id, it.name, it.latitude, it.longitude, it.sortOrder)
        }

        return json.encodeToString(
            ExportData.serializer(),
            ExportData(clothingItems, snapshots, entries, items, locations)
        )
    }

    suspend fun importFromJson(jsonString: String) {
        val data = json.decodeFromString(ExportData.serializer(), jsonString)

        outfitRepository.deleteAllOutfitItems()
        outfitRepository.deleteAllEntries()
        weatherRepository.deleteAllSnapshots()
        catalogRepository.deleteAll()
        locationRepository.deleteAll()

        catalogRepository.insertAll(data.clothingItems.map {
            ClothingItem(it.id, Sport.valueOf(it.sport), BodyPart.valueOf(it.bodyPart), it.name, it.isDefault)
        })

        data.weatherSnapshots.forEach {
            weatherRepository.saveSnapshot(
                WeatherSnapshot(
                    it.id, it.timestamp, it.latitude, it.longitude, it.locationName,
                    it.temperatureCelsius, it.apparentTemperatureCelsius, it.windSpeedKmh,
                    it.windDirectionDegrees, it.uvIndex, it.cloudCoverPercent,
                    it.precipitationProbabilityPercent
                )
            )
        }

        data.outfitEntries.forEach {
            outfitRepository.insertEntry(
                OutfitEntry(it.id, it.weatherSnapshotId, Sport.valueOf(it.sport), it.comfortRating, it.createdAt, it.ratedAt, it.notes)
            )
        }

        outfitRepository.insertOutfitItems(data.outfitItems.map {
            OutfitItem(it.outfitEntryId, it.clothingItemId)
        })

        locationRepository.insertAll(data.favoriteLocations.map {
            FavoriteLocation(it.id, it.name, it.latitude, it.longitude, it.sortOrder)
        })
    }
}
