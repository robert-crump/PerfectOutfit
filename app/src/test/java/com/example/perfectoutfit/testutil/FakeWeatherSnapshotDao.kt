package com.example.perfectoutfit.testutil

import com.example.perfectoutfit.core.database.dao.WeatherSnapshotDao
import com.example.perfectoutfit.core.model.WeatherSnapshot

class FakeWeatherSnapshotDao : WeatherSnapshotDao {
    private val snapshots = mutableMapOf<Long, WeatherSnapshot>()
    private var nextId = 1L

    override suspend fun insert(snapshot: WeatherSnapshot): Long {
        val id = if (snapshot.id != 0L) snapshot.id else nextId++
        snapshots[id] = snapshot.copy(id = id)
        return id
    }

    override suspend fun getById(id: Long): WeatherSnapshot? = snapshots[id]

    override suspend fun getAll(): List<WeatherSnapshot> = snapshots.values.sortedBy { it.id }

    override suspend fun deleteAll() {
        snapshots.clear()
    }
}
