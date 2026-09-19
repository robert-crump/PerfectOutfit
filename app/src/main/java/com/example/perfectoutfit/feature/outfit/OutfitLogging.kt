package com.example.perfectoutfit.feature.outfit

import com.example.perfectoutfit.core.database.DatabaseTransactionRunner
import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.database.dao.OutfitItemDao
import com.example.perfectoutfit.core.database.dao.WeatherSnapshotDao
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.OutfitItem
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.notification.RatingReminder
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.home.toWeatherSnapshot
import kotlinx.coroutines.flow.Flow
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Whether an outfit is logged as it happens (live) or after the fact (past). */
enum class LogMode { LIVE, PAST }

/** Where the logged workout took place. A blank [name] falls back to "Current Location". */
data class LogLocation(val name: String, val lat: Double, val lon: Double) {
    val displayName: String get() = name.ifBlank { DEFAULT_NAME }

    companion object {
        const val DEFAULT_NAME = "Current Location"
    }
}

/**
 * Outfit logging: the one path that writes outfit entries. It owns the snapshot -> entry ->
 * reminder sequence, the `createdAt` rule (an entry is stamped with the workout hour, so
 * History sorts by when the workout happened), and the reminder rule (a reminder is
 * scheduled only for live logs or entries that are still unrated).
 */
@Singleton
class OutfitLogging @Inject constructor(
    private val outfitEntryDao: OutfitEntryDao,
    private val outfitItemDao: OutfitItemDao,
    private val weatherSnapshotDao: WeatherSnapshotDao,
    private val transactionRunner: DatabaseTransactionRunner,
    private val ratingReminder: RatingReminder
) {
    /** Logs an outfit worn during [hour]; returns the new entry id. */
    suspend fun log(
        hour: HourlyWeather,
        location: LogLocation,
        sport: Sport,
        clothingItemIds: Collection<Long>,
        rating: Int? = null,
        notes: String = "",
        workoutDurationHours: Int = 1,
        mode: LogMode
    ): Long {
        val workoutMs = hour.time.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000
        val entryId = transactionRunner.runInTransaction {
            val snapshotId = weatherSnapshotDao.insert(
                hour.toWeatherSnapshot(location.lat, location.lon, location.displayName)
            )
            val id = outfitEntryDao.insert(
                OutfitEntry(
                    weatherSnapshotId = snapshotId,
                    sport = sport,
                    comfortRating = rating,
                    createdAt = workoutMs,
                    ratedAt = rating?.let { System.currentTimeMillis() },
                    notes = notes
                )
            )
            insertItems(id, clothingItemIds)
            id
        }
        if (mode == LogMode.LIVE || rating == null) {
            ratingReminder.show(entryId, sport, workoutMs, workoutDurationHours)
        }
        return entryId
    }

    /** Sets the rating and stamps `ratedAt`; the pending reminder (if any) is cancelled. */
    suspend fun rate(entryId: Long, rating: Int) {
        ratingReminder.cancel(entryId)
        val entry = outfitEntryDao.getById(entryId) ?: return
        outfitEntryDao.update(entry.copy(comfortRating = rating, ratedAt = System.currentTimeMillis()))
    }

    /** Replaces items and notes of an existing entry, and rates it if [rating] is given, atomically. */
    suspend fun update(entryId: Long, clothingItemIds: Collection<Long>, notes: String, rating: Int?) {
        transactionRunner.runInTransaction {
            outfitItemDao.deleteByEntryId(entryId)
            insertItems(entryId, clothingItemIds)
            outfitEntryDao.updateNotes(entryId, notes)
            if (rating != null) rate(entryId, rating)
        }
    }

    suspend fun delete(entryId: Long) = outfitEntryDao.deleteById(entryId)

    /** Re-inserts a deleted entry with its original id and items. */
    suspend fun restore(entry: OutfitEntry, clothingItemIds: Collection<Long>) {
        transactionRunner.runInTransaction {
            outfitEntryDao.insert(entry)
            insertItems(entry.id, clothingItemIds)
        }
    }

    suspend fun getEntryWithDetails(entryId: Long): OutfitEntryWithDetails? =
        outfitEntryDao.getWithDetailsById(entryId)

    fun allEntries(): Flow<List<OutfitEntryWithDetails>> = outfitEntryDao.getAllWithDetails()

    fun entriesBySport(sport: Sport): Flow<List<OutfitEntryWithDetails>> =
        outfitEntryDao.getAllWithDetailsBySport(sport)

    private suspend fun insertItems(entryId: Long, clothingItemIds: Collection<Long>) {
        outfitItemDao.insertAll(clothingItemIds.map { OutfitItem(outfitEntryId = entryId, clothingItemId = it) })
    }
}
