package com.example.perfectoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.perfectoutfit.core.model.FavoriteLocation
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteLocationDao {
    @Query("SELECT * FROM favorite_locations ORDER BY name COLLATE NOCASE ASC")
    fun getAll(): Flow<List<FavoriteLocation>>

    @Query("SELECT * FROM favorite_locations ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllSync(): List<FavoriteLocation>

    @Query("SELECT * FROM favorite_locations WHERE id = :id")
    suspend fun getById(id: Long): FavoriteLocation?

    @Insert
    suspend fun insert(location: FavoriteLocation): Long

    @Update
    suspend fun update(location: FavoriteLocation)

    @Query("DELETE FROM favorite_locations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM favorite_locations")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM favorite_locations")
    suspend fun getNextSortOrder(): Int
}
