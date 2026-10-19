package com.example.perfectoutfit.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ClothingItemActivityType
import kotlinx.coroutines.flow.Flow

@Dao
interface ClothingItemDao {
    @Query("""
        SELECT ci.* FROM clothing_items ci
        JOIN clothing_item_activity_types t ON t.clothingItemId = ci.id
        WHERE t.activityType = :activityType
        ORDER BY ci.bodyPart, ci.name
    """)
    fun getByActivityType(activityType: ActivityType): Flow<List<ClothingItem>>

    @Query("SELECT * FROM clothing_items WHERE id = :id")
    suspend fun getById(id: Long): ClothingItem?

    @Query("SELECT * FROM clothing_items ORDER BY bodyPart, name, id")
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

    @Query("SELECT * FROM clothing_item_activity_types WHERE clothingItemId IN (:itemIds) ORDER BY clothingItemId, activityType")
    suspend fun getActivityTypesOf(itemIds: List<Long>): List<ClothingItemActivityType>

    @Query("SELECT * FROM clothing_item_activity_types ORDER BY clothingItemId, activityType")
    suspend fun getAllActivityTypes(): List<ClothingItemActivityType>

    @Insert
    suspend fun insertActivityTypes(rows: List<ClothingItemActivityType>)

    @Query("DELETE FROM clothing_item_activity_types WHERE clothingItemId = :itemId")
    suspend fun deleteActivityTypesOf(itemId: Long)

    @Query("DELETE FROM clothing_item_activity_types")
    suspend fun deleteAllActivityTypes()
}
