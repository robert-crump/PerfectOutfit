package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.database.dao.OutfitItemDao
import com.example.perfectoutfit.core.model.OutfitItem

class FakeOutfitItemDao : OutfitItemDao {
    private val items = mutableListOf<OutfitItem>()

    override suspend fun insert(item: OutfitItem) {
        items.add(item)
    }

    override suspend fun insertAll(items: List<OutfitItem>) {
        this.items.addAll(items)
    }

    override suspend fun getByEntryId(entryId: Long): List<OutfitItem> =
        items.filter { it.outfitEntryId == entryId }

    override suspend fun deleteByEntryId(entryId: Long) {
        items.removeAll { it.outfitEntryId == entryId }
    }

    override suspend fun getAll(): List<OutfitItem> = items.toList()

    override suspend fun deleteAll() {
        items.clear()
    }
}
