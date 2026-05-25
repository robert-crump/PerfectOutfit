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
        WHERE oe.sport = :sport
        AND oe.comfortRating IS NOT NULL
    """)
    suspend fun getRatedEntriesWithDetails(sport: Sport): List<OutfitEntryWithDetails>

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
