package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.database.dao.WorkoutHourDao
import com.example.perfectoutfit.core.model.WorkoutHour

class FakeWorkoutHourDao : WorkoutHourDao {
    private val hours = mutableListOf<WorkoutHour>()

    override suspend fun insertAll(hours: List<WorkoutHour>) {
        hours.forEach { hour ->
            require(this.hours.none { it.outfitEntryId == hour.outfitEntryId && it.timestamp == hour.timestamp }) {
                "Duplicate workout hour $hour"
            }
        }
        this.hours += hours
    }

    override suspend fun getByEntryId(entryId: Long): List<WorkoutHour> =
        hours.filter { it.outfitEntryId == entryId }.sortedBy { it.timestamp }

    override suspend fun getAll(): List<WorkoutHour> = hours.sortedWith(compareBy({ it.outfitEntryId }, { it.timestamp }))

    override suspend fun deleteAll() {
        hours.clear()
    }
}
