package com.example.perfectoutfit.feature.location

import com.example.perfectoutfit.core.database.dao.FavoriteLocationDao
import com.example.perfectoutfit.core.model.FavoriteLocation
import com.example.perfectoutfit.core.network.GeocodingApi
import com.example.perfectoutfit.core.network.GeocodingResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val favoriteLocationDao: FavoriteLocationDao,
    private val geocodingApi: GeocodingApi
) {
    val favoriteLocations: Flow<List<FavoriteLocation>> = favoriteLocationDao.getAll()

    suspend fun searchCities(query: String): List<GeocodingResult> {
        if (query.length < 2) return emptyList()
        return try {
            geocodingApi.search(query).results
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addFavorite(name: String, lat: Double, lon: Double): Long {
        val sortOrder = favoriteLocationDao.getNextSortOrder()
        return favoriteLocationDao.insert(
            FavoriteLocation(name = name, latitude = lat, longitude = lon, sortOrder = sortOrder)
        )
    }

    suspend fun removeFavorite(id: Long) {
        favoriteLocationDao.deleteById(id)
    }

    suspend fun getFavoriteById(id: Long): FavoriteLocation? {
        return favoriteLocationDao.getById(id)
    }

    suspend fun getAllFavoritesSync(): List<FavoriteLocation> {
        return favoriteLocationDao.getAllSync()
    }
}
