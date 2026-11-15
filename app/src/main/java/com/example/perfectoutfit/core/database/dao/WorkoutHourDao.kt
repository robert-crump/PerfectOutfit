package com.example.perfectoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.perfectoutfit.core.model.WorkoutHour

@Dao
interface WorkoutHourDao {
    @Insert
    suspend fun insertAll(hours: List<WorkoutHour>)

    @Query("SELECT * FROM workout_hours WHERE outfitEntryId = :entryId ORDER BY timestamp")
    suspend fun getByEntryId(entryId: Long): List<WorkoutHour>

    @Query("SELECT * FROM workout_hours ORDER BY outfitEntryId, timestamp")
    suspend fun getAll(): List<WorkoutHour>

    @Query("DELETE FROM workout_hours")
    suspend fun deleteAll()
}
