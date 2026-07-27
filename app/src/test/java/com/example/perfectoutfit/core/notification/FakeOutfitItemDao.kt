package com.example.perfectoutfit.core.notification

import com.example.perfectoutfit.core.database.dao.OutfitItemDao
import com.example.perfectoutfit.core.model.OutfitItem

class FakeOutfitItemDao : OutfitItemDao {
    override suspend fun insert(item: OutfitItem) = throw UnsupportedOperationException()
    override suspend fun insertAll(items: List<OutfitItem>) = throw UnsupportedOperationException()
    override suspend fun getByEntryId(entryId: Long): List<OutfitItem> = throw UnsupportedOperationException()
    override suspend fun deleteByEntryId(entryId: Long) = throw UnsupportedOperationException()
    override suspend fun getAll(): List<OutfitItem> = throw UnsupportedOperationException()
    override suspend fun deleteAll() = throw UnsupportedOperationException()
}
