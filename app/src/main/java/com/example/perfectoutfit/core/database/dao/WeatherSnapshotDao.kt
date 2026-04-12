package com.example.perfectoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.perfectoutfit.core.model.WeatherSnapshot

@Dao
interface WeatherSnapshotDao {
    @Insert
    suspend fun insert(snapshot: WeatherSnapshot): Long

    @Query("SELECT * FROM weather_snapshots WHERE id = :id")
    suspend fun getById(id: Long): WeatherSnapshot?

    @Query("SELECT * FROM weather_snapshots ORDER BY timestamp DESC")
    suspend fun getAll(): List<WeatherSnapshot>

    @Query("DELETE FROM weather_snapshots")
    suspend fun deleteAll()
}
