package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.database.dao.OutfitItemDao
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.Sport
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class OutfitRepository @Inject constructor(
    private val outfitEntryDao: OutfitEntryDao,
    private val outfitItemDao: OutfitItemDao
) {
    suspend fun createEntry(entry: OutfitEntry, clothingItemIds: List<Long>): Long {
        val entryId = outfitEntryDao.insert(entry)
        val items = clothingItemIds.map { OutfitItem(outfitEntryId = entryId, clothingItemId = it) }
        outfitItemDao.insertAll(items)
        return entryId
    }

    suspend fun rateEntry(entryId: Long, rating: Int) {
        val entry = outfitEntryDao.getById(entryId) ?: return
        outfitEntryDao.update(
            entry.copy(comfortRating = rating, ratedAt = System.currentTimeMillis())
        )
    }

    suspend fun updateEntryItems(entryId: Long, clothingItemIds: List<Long>) {
        outfitItemDao.deleteByEntryId(entryId)
        val items = clothingItemIds.map { OutfitItem(outfitEntryId = entryId, clothingItemId = it) }
        outfitItemDao.insertAll(items)
    }

    suspend fun getEntryWithDetails(entryId: Long): OutfitEntryWithDetails? {
        return outfitEntryDao.getWithDetailsById(entryId)
    }

    fun getAllEntriesWithDetails(): Flow<List<OutfitEntryWithDetails>> {
        return outfitEntryDao.getAllWithDetails()
    }

    fun getEntriesBySport(sport: Sport): Flow<List<OutfitEntryWithDetails>> {
        return outfitEntryDao.getAllWithDetailsBySport(sport)
    }

    suspend fun getRatedEntries(sport: Sport): List<OutfitEntryWithDetails> {
        return outfitEntryDao.getRatedEntriesWithDetails(sport)
    }

    suspend fun findRecommendation(
        sport: Sport,
        temp: Double,
        useApparent: Boolean = true
    ): OutfitEntryWithDetails? {
        val candidates = outfitEntryDao.getRatedEntriesWithDetails(sport)
        return RecommendationPolicy.findRecommendation(candidates, temp.roundToInt(), useApparent)
    }

    suspend fun updateNotes(entryId: Long, notes: String) = outfitEntryDao.updateNotes(entryId, notes)

    suspend fun deleteEntry(id: Long) = outfitEntryDao.deleteById(id)

    suspend fun restoreEntry(entry: OutfitEntry, clothingItemIds: List<Long>) {
        outfitEntryDao.insert(entry)
        val items = clothingItemIds.map { OutfitItem(outfitEntryId = entry.id, clothingItemId = it) }
        outfitItemDao.insertAll(items)
    }

    suspend fun getLikelyItemIds(sport: Sport, temp: Double, useApparent: Boolean): Set<Long> {
        val candidates = outfitEntryDao.getRatedEntriesWithDetails(sport)
        return RecommendationPolicy.likelyItemIds(candidates, temp.roundToInt(), useApparent)
    }

    suspend fun getAllEntries(): List<OutfitEntry> = outfitEntryDao.getAll()
    suspend fun getAllOutfitItems(): List<OutfitItem> = outfitItemDao.getAll()
    suspend fun deleteAllEntries() = outfitEntryDao.deleteAll()
    suspend fun deleteAllOutfitItems() = outfitItemDao.deleteAll()
    suspend fun insertEntry(entry: OutfitEntry): Long = outfitEntryDao.insert(entry)
    suspend fun insertOutfitItems(items: List<OutfitItem>) = outfitItemDao.insertAll(items)
}
