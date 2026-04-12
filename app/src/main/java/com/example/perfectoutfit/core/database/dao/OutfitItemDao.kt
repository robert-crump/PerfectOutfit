package com.example.perfectoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.perfectoutfit.core.model.OutfitItem

@Dao
interface OutfitItemDao {
    @Insert
    suspend fun insert(item: OutfitItem)

    @Insert
    suspend fun insertAll(items: List<OutfitItem>)

    @Query("SELECT * FROM outfit_items WHERE outfitEntryId = :entryId")
    suspend fun getByEntryId(entryId: Long): List<OutfitItem>

    @Query("DELETE FROM outfit_items WHERE outfitEntryId = :entryId")
    suspend fun deleteByEntryId(entryId: Long)

    @Query("SELECT * FROM outfit_items")
    suspend fun getAll(): List<OutfitItem>

    @Query("DELETE FROM outfit_items")
    suspend fun deleteAll()
}
