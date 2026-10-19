package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.database.dao.ClothingItemDao
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ClothingItemActivityType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** In-memory items and item ↔ activity-type rows; deleting an item cascades to its rows, as in Room. */
class FakeClothingItemDao : ClothingItemDao {
    private val items = MutableStateFlow<Map<Long, ClothingItem>>(emptyMap())
    private val activityTypes = MutableStateFlow<Set<ClothingItemActivityType>>(emptySet())
    private var nextId = 1L

    override fun getByActivityType(activityType: ActivityType): Flow<List<ClothingItem>> =
        combine(items, activityTypes) { current, rows ->
            val ids = rows.filter { it.activityType == activityType }.map { it.clothingItemId }.toSet()
            current.values.filter { it.id in ids }.sortedWith(compareBy({ it.bodyPart.name }, { it.name }))
        }

    override suspend fun getById(id: Long): ClothingItem? = items.value[id]

    override suspend fun getAll(): List<ClothingItem> = items.value.values.sortedBy { it.id }

    override suspend fun insert(item: ClothingItem): Long {
        val id = if (item.id != 0L) item.id else nextId++
        items.value = items.value + (id to item.copy(id = id))
        return id
    }

    override suspend fun insertAll(items: List<ClothingItem>) {
        items.forEach { insert(it) }
    }

    override suspend fun update(item: ClothingItem) {
        items.value = items.value + (item.id to item)
    }

    override suspend fun deleteById(id: Long) {
        activityTypes.value = activityTypes.value.filterNot { it.clothingItemId == id }.toSet()
        items.value = items.value - id
    }

    override suspend fun deleteAll() {
        activityTypes.value = emptySet()
        items.value = emptyMap()
    }

    override suspend fun count(): Int = items.value.size

    override suspend fun getActivityTypesOf(itemIds: List<Long>): List<ClothingItemActivityType> =
        getAllActivityTypes().filter { it.clothingItemId in itemIds }

    override suspend fun getAllActivityTypes(): List<ClothingItemActivityType> =
        activityTypes.value.sortedWith(compareBy({ it.clothingItemId }, { it.activityType.name }))

    override suspend fun insertActivityTypes(rows: List<ClothingItemActivityType>) {
        activityTypes.value = activityTypes.value + rows
    }

    override suspend fun deleteActivityTypesOf(itemId: Long) {
        activityTypes.value = activityTypes.value.filterNot { it.clothingItemId == itemId }.toSet()
    }

    override suspend fun deleteAllActivityTypes() {
        activityTypes.value = emptySet()
    }
}
