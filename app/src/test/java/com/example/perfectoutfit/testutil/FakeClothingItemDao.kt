package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.database.dao.ClothingItemDao
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.Sport
import kotlinx.coroutines.flow.Flow

class FakeClothingItemDao : ClothingItemDao {
    private val items = mutableMapOf<Long, ClothingItem>()
    private var nextId = 1L

    override fun getBySport(sport: Sport): Flow<List<ClothingItem>> =
        throw UnsupportedOperationException()

    override fun getBySportAndBodyPart(sport: Sport, bodyPart: BodyPart): Flow<List<ClothingItem>> =
        throw UnsupportedOperationException()

    override suspend fun getById(id: Long): ClothingItem? = items[id]

    override suspend fun getAll(): List<ClothingItem> = items.values.sortedBy { it.id }

    override suspend fun insert(item: ClothingItem): Long {
        val id = if (item.id != 0L) item.id else nextId++
        items[id] = item.copy(id = id)
        return id
    }

    override suspend fun insertAll(items: List<ClothingItem>) {
        items.forEach { insert(it) }
    }

    override suspend fun update(item: ClothingItem) {
        items[item.id] = item
    }

    override suspend fun deleteById(id: Long) {
        items.remove(id)
    }

    override suspend fun deleteAll() {
        items.clear()
    }

    override suspend fun count(): Int = items.size
}
