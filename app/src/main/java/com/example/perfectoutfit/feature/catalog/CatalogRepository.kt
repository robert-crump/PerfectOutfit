package com.example.perfectoutfit.feature.catalog

import com.example.perfectoutfit.core.database.dao.ClothingItemDao
import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.Sport
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val clothingItemDao: ClothingItemDao,
    private val outfitEntryDao: OutfitEntryDao
) {
    fun getItemsBySport(sport: Sport): Flow<List<ClothingItem>> =
        clothingItemDao.getBySport(sport)

    fun getItemsBySportAndBodyPart(sport: Sport, bodyPart: BodyPart): Flow<List<ClothingItem>> =
        clothingItemDao.getBySportAndBodyPart(sport, bodyPart)

    suspend fun addItem(sport: Sport, bodyPart: BodyPart, name: String): Long =
        clothingItemDao.insert(ClothingItem(sport = sport, bodyPart = bodyPart, name = name))

    suspend fun renameItem(item: ClothingItem, newName: String) {
        clothingItemDao.update(item.copy(name = newName))
    }

    suspend fun deleteItem(id: Long) {
        clothingItemDao.deleteById(id)
    }

    suspend fun getAllItems(): List<ClothingItem> = clothingItemDao.getAll()

    suspend fun insertAll(items: List<ClothingItem>) = clothingItemDao.insertAll(items)

    suspend fun deleteAll() = clothingItemDao.deleteAll()

    suspend fun count(): Int = clothingItemDao.count()

    suspend fun countEntriesWithItems(itemIds: List<Long>): Int =
        if (itemIds.isEmpty()) 0 else outfitEntryDao.countEntriesWithAnyItem(itemIds)
}
