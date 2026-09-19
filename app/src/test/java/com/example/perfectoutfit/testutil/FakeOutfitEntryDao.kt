package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import kotlinx.coroutines.flow.Flow

class FakeOutfitEntryDao : OutfitEntryDao {
    private val entries = mutableMapOf<Long, OutfitEntry>()
    private var nextId = 1L

    var lastUpdated: OutfitEntry? = null
        private set

    fun seed(entryId: Long, rating: Int? = null) {
        entries[entryId] = OutfitEntry(
            id = entryId,
            weatherSnapshotId = 1L,
            sport = Sport.CYCLING,
            comfortRating = rating,
            createdAt = 0L
        )
    }

    override suspend fun insert(entry: OutfitEntry): Long {
        val id = if (entry.id != 0L) entry.id else nextId++
        entries[id] = entry.copy(id = id)
        return id
    }

    override suspend fun update(entry: OutfitEntry) {
        entries[entry.id] = entry
        lastUpdated = entry
    }

    override suspend fun getById(id: Long): OutfitEntry? = entries[id]

    override suspend fun getWithDetailsById(id: Long): OutfitEntryWithDetails? =
        throw UnsupportedOperationException()

    override fun getAllWithDetailsBySport(sport: Sport): Flow<List<OutfitEntryWithDetails>> =
        throw UnsupportedOperationException()

    override fun getAllWithDetails(): Flow<List<OutfitEntryWithDetails>> =
        throw UnsupportedOperationException()

    /** Candidates returned by [getRatedEntriesWithDetails] (filtered to rated entries of the sport). */
    var ratedEntries: List<OutfitEntryWithDetails> = emptyList()

    /** Runs at the start of every [getRatedEntriesWithDetails] call, to simulate in-flight changes. */
    var onQuery: () -> Unit = {}

    override suspend fun getRatedEntriesWithDetails(sport: Sport): List<OutfitEntryWithDetails> {
        onQuery()
        return ratedEntries.filter { it.entry.sport == sport && it.entry.comfortRating != null }
    }

    override suspend fun countEntriesWithAnyItem(itemIds: List<Long>): Int =
        throw UnsupportedOperationException()

    override suspend fun getAll(): List<OutfitEntry> = entries.values.sortedBy { it.id }

    override suspend fun deleteById(id: Long) {
        entries.remove(id)
    }

    override suspend fun updateNotes(entryId: Long, notes: String) =
        throw UnsupportedOperationException()

    override suspend fun deleteAll() {
        entries.clear()
    }
}
