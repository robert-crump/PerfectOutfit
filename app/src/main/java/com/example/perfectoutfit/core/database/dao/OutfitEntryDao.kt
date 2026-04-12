package com.example.perfectoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import kotlinx.coroutines.flow.Flow

@Dao
interface OutfitEntryDao {
    @Insert
    suspend fun insert(entry: OutfitEntry): Long

    @Update
    suspend fun update(entry: OutfitEntry)

    @Query("SELECT * FROM outfit_entries WHERE id = :id")
    suspend fun getById(id: Long): OutfitEntry?

    @Transaction
    @Query("SELECT * FROM outfit_entries WHERE id = :id")
    suspend fun getWithDetailsById(id: Long): OutfitEntryWithDetails?

    @Transaction
    @Query("SELECT * FROM outfit_entries WHERE sport = :sport ORDER BY createdAt DESC")
    fun getAllWithDetailsBySport(sport: Sport): Flow<List<OutfitEntryWithDetails>>

    @Transaction
    @Query("SELECT * FROM outfit_entries ORDER BY createdAt DESC")
    fun getAllWithDetails(): Flow<List<OutfitEntryWithDetails>>

    @Transaction
    @Query("""
        SELECT oe.* FROM outfit_entries oe
        INNER JOIN weather_snapshots ws ON oe.weatherSnapshotId = ws.id
        WHERE oe.sport = :sport
        AND oe.comfortRating IS NOT NULL
        AND ROUND(ws.apparentTemperatureCelsius) = :temp
        ORDER BY ws.timestamp DESC
        LIMIT 1
    """)
    suspend fun findNewestExactMatch(sport: Sport, temp: Int): OutfitEntryWithDetails?

    @Transaction
    @Query("""
        SELECT oe.* FROM outfit_entries oe
        INNER JOIN weather_snapshots ws ON oe.weatherSnapshotId = ws.id
        WHERE oe.sport = :sport
        AND oe.comfortRating IS NOT NULL
        AND ROUND(ws.temperatureCelsius) = :temp
        ORDER BY ws.timestamp DESC
        LIMIT 1
    """)
    suspend fun findNewestExactMatchByRealTemp(sport: Sport, temp: Int): OutfitEntryWithDetails?

    @Transaction
    @Query("""
        SELECT oe.* FROM outfit_entries oe
        INNER JOIN weather_snapshots ws ON oe.weatherSnapshotId = ws.id
        WHERE oe.sport = :sport
        AND oe.comfortRating IS NOT NULL
        AND ROUND(ws.apparentTemperatureCelsius) BETWEEN :minTemp AND :maxTemp
        ORDER BY CASE oe.comfortRating WHEN 0 THEN 0 WHEN 1 THEN 1 ELSE 2 END ASC, ws.timestamp DESC
        LIMIT 1
    """)
    suspend fun findBestMatch(sport: Sport, minTemp: Int, maxTemp: Int): OutfitEntryWithDetails?

    @Transaction
    @Query("""
        SELECT oe.* FROM outfit_entries oe
        INNER JOIN weather_snapshots ws ON oe.weatherSnapshotId = ws.id
        WHERE oe.sport = :sport
        AND oe.comfortRating IS NOT NULL
        AND ROUND(ws.temperatureCelsius) BETWEEN :minTemp AND :maxTemp
        ORDER BY CASE oe.comfortRating WHEN 0 THEN 0 WHEN 1 THEN 1 ELSE 2 END ASC, ws.timestamp DESC
        LIMIT 1
    """)
    suspend fun findBestMatchByRealTemp(sport: Sport, minTemp: Int, maxTemp: Int): OutfitEntryWithDetails?

    @Query("""
        SELECT DISTINCT oi.clothingItemId
        FROM outfit_items oi
        INNER JOIN outfit_entries oe ON oi.outfitEntryId = oe.id
        INNER JOIN weather_snapshots ws ON oe.weatherSnapshotId = ws.id
        WHERE oe.sport = :sport
        AND oe.comfortRating = 0
        AND ROUND(ws.apparentTemperatureCelsius) BETWEEN :minTemp AND :maxTemp
    """)
    suspend fun getClothingItemIdsForApparentTemp(sport: Sport, minTemp: Int, maxTemp: Int): List<Long>

    @Query("""
        SELECT DISTINCT oi.clothingItemId
        FROM outfit_items oi
        INNER JOIN outfit_entries oe ON oi.outfitEntryId = oe.id
        INNER JOIN weather_snapshots ws ON oe.weatherSnapshotId = ws.id
        WHERE oe.sport = :sport
        AND oe.comfortRating = 0
        AND ROUND(ws.temperatureCelsius) BETWEEN :minTemp AND :maxTemp
    """)
    suspend fun getClothingItemIdsForRealTemp(sport: Sport, minTemp: Int, maxTemp: Int): List<Long>

    @Query("""
        SELECT COUNT(DISTINCT oi.outfitEntryId)
        FROM outfit_items oi
        WHERE oi.clothingItemId IN (:itemIds)
    """)
    suspend fun countEntriesWithAnyItem(itemIds: List<Long>): Int

    @Query("SELECT * FROM outfit_entries ORDER BY createdAt DESC")
    suspend fun getAll(): List<OutfitEntry>

    @Query("DELETE FROM outfit_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE outfit_entries SET notes = :notes WHERE id = :entryId")
    suspend fun updateNotes(entryId: Long, notes: String)

    @Query("DELETE FROM outfit_entries")
    suspend fun deleteAll()
}
