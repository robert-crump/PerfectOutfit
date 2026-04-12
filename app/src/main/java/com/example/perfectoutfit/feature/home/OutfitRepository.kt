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

    suspend fun findRecommendation(
        sport: Sport,
        temp: Double,
        useApparent: Boolean = true
    ): OutfitEntryWithDetails? {
        val roundedTemp = temp.roundToInt()

        // Step 1: exact temperature → newest entry regardless of rating
        val exact = if (useApparent)
            outfitEntryDao.findNewestExactMatch(sport, roundedTemp)
        else
            outfitEntryDao.findNewestExactMatchByRealTemp(sport, roundedTemp)
        if (exact != null) return exact

        // Step 2: ±1°C → best rating first, then newest
        val plusMinus1 = if (useApparent)
            outfitEntryDao.findBestMatch(sport, roundedTemp - 1, roundedTemp + 1)
        else
            outfitEntryDao.findBestMatchByRealTemp(sport, roundedTemp - 1, roundedTemp + 1)
        if (plusMinus1 != null) return plusMinus1

        // Step 3: ±2°C → best rating first, then newest
        return if (useApparent)
            outfitEntryDao.findBestMatch(sport, roundedTemp - 2, roundedTemp + 2)
        else
            outfitEntryDao.findBestMatchByRealTemp(sport, roundedTemp - 2, roundedTemp + 2)
    }

    suspend fun updateNotes(entryId: Long, notes: String) = outfitEntryDao.updateNotes(entryId, notes)

    suspend fun deleteEntry(id: Long) = outfitEntryDao.deleteById(id)

    suspend fun restoreEntry(entry: OutfitEntry, clothingItemIds: List<Long>) {
        outfitEntryDao.insert(entry)
        val items = clothingItemIds.map { OutfitItem(outfitEntryId = entry.id, clothingItemId = it) }
        outfitItemDao.insertAll(items)
    }

    suspend fun getLikelyItemIds(sport: Sport, temp: Double, useApparent: Boolean): Set<Long> {
        val roundedTemp = temp.roundToInt()
        return if (useApparent) {
            outfitEntryDao.getClothingItemIdsForApparentTemp(sport, roundedTemp - 2, roundedTemp + 2)
        } else {
            outfitEntryDao.getClothingItemIdsForRealTemp(sport, roundedTemp - 2, roundedTemp + 2)
        }.toSet()
    }

    suspend fun getAllEntries(): List<OutfitEntry> = outfitEntryDao.getAll()
    suspend fun getAllOutfitItems(): List<OutfitItem> = outfitItemDao.getAll()
    suspend fun deleteAllEntries() = outfitEntryDao.deleteAll()
    suspend fun deleteAllOutfitItems() = outfitItemDao.deleteAll()
    suspend fun insertEntry(entry: OutfitEntry): Long = outfitEntryDao.insert(entry)
    suspend fun insertOutfitItems(items: List<OutfitItem>) = outfitItemDao.insertAll(items)
}
