package com.example.perfectoutfit.feature.catalog

import com.example.perfectoutfit.core.database.DatabaseTransactionRunner
import com.example.perfectoutfit.core.database.dao.ClothingItemDao
import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ClothingItemActivityType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val clothingItemDao: ClothingItemDao,
    private val outfitEntryDao: OutfitEntryDao,
    private val transactionRunner: DatabaseTransactionRunner
) {
    fun getItemsByActivityType(activityType: ActivityType): Flow<List<ClothingItem>> =
        clothingItemDao.getByActivityType(activityType)

    /** Adds an item belonging to [activityTypes] (at least one); returns its id. */
    suspend fun addItem(bodyPart: BodyPart, name: String, activityTypes: Set<ActivityType>): Long {
        require(activityTypes.isNotEmpty()) { "An item belongs to at least one activity type" }
        return transactionRunner.runInTransaction {
            val id = clothingItemDao.insert(ClothingItem(bodyPart = bodyPart, name = name))
            clothingItemDao.insertActivityTypes(activityTypes.map { ClothingItemActivityType(id, it) })
            id
        }
    }

    /** Renames [item] and replaces its activity types with [activityTypes] (at least one). */
    suspend fun editItem(item: ClothingItem, newName: String, activityTypes: Set<ActivityType>) {
        require(activityTypes.isNotEmpty()) { "An item belongs to at least one activity type" }
        transactionRunner.runInTransaction {
            clothingItemDao.update(item.copy(name = newName))
            clothingItemDao.deleteActivityTypesOf(item.id)
            clothingItemDao.insertActivityTypes(activityTypes.map { ClothingItemActivityType(item.id, it) })
        }
    }

    /** The activity types of each of [itemIds]; items without any are absent. */
    suspend fun activityTypesOf(itemIds: Collection<Long>): Map<Long, Set<ActivityType>> =
        if (itemIds.isEmpty()) emptyMap()
        else clothingItemDao.getActivityTypesOf(itemIds.toList())
            .groupBy({ it.clothingItemId }, { it.activityType })
            .mapValues { (_, types) -> types.toSet() }

    /** Deletes the items everywhere: from every activity type and every outfit entry. */
    suspend fun deleteItems(ids: Collection<Long>) {
        transactionRunner.runInTransaction {
            ids.forEach { clothingItemDao.deleteById(it) }
        }
    }

    suspend fun count(): Int = clothingItemDao.count()

    suspend fun countEntriesWithItems(itemIds: List<Long>): Int =
        if (itemIds.isEmpty()) 0 else outfitEntryDao.countEntriesWithAnyItem(itemIds)
}
