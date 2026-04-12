package com.example.perfectoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.Sport
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingItemDao {
    @Query("SELECT * FROM clothing_items WHERE sport = :sport ORDER BY bodyPart, name")
    fun getBySport(sport: Sport): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE sport = :sport AND bodyPart = :bodyPart ORDER BY name")
    fun getBySportAndBodyPart(sport: Sport, bodyPart: BodyPart): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getById(id: Long): ClothingItem?

    @Query("SELECT * FROM clothing_items ORDER BY sport, bodyPart, name")
    suspend fun getAll(): List<ClothingItem>

    @Insert
    suspend fun insert(item: ClothingItem): Long

    @Insert
    suspend fun insertAll(items: List<ClothingItem>)

    @Update
    suspend fun update(item: ClothingItem)

    @Query("DELETE FROM clothing_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM clothing_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM clothing_items")
    suspend fun count(): Int
}
