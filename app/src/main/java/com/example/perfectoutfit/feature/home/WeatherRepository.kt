package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.database.dao.WeatherSnapshotDao
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.core.network.OpenMeteoApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val openMeteoApi: OpenMeteoApi,
    private val weatherSnapshotDao: WeatherSnapshotDao
) {
    /** All hours from the last fetch (past 2 days + today + tomorrow). */
    var cachedAllHours: List<HourlyWeather> = emptyList()
        private set

    var cachedLat: Double = 0.0
        private set

    var cachedLon: Double = 0.0
        private set

    var cachedLocationName: String = ""
        private set

    /**
     * Item IDs to pre-fill in the custom outfit screen.
     * Set by HomeViewModel before navigating to NewOutfit.
     */
    var pendingNewOutfitItemIds: List<Long> = emptyList()

    /** The LocalDateTime of the hour active on the Home Screen at navigation time. */
    var cachedSelectedHourTime: java.time.LocalDateTime? = null

    /** Workout duration (hours) selected on the Home Screen, used for live-mode notifications. */
    var cachedWorkoutDurationHours: Int = 1

    suspend fun fetchWeather(lat: Double, lon: Double, locationName: String = ""): List<HourlyWeather> {
        val response = openMeteoApi.getForecast(lat, lon)
        val allHours = WeatherMapper.extractAllHours(response)
        cachedAllHours = allHours
        cachedLat = lat
        cachedLon = lon
        cachedLocationName = locationName
        return allHours
    }

    /** Fetches weather without overwriting the main cached data. */
    suspend fun fetchWeatherOnly(lat: Double, lon: Double): List<HourlyWeather> {
        val response = openMeteoApi.getForecast(lat, lon)
        return WeatherMapper.extractAllHours(response)
    }

    /** Fetches weather for a specific date without affecting the main cache. */
    suspend fun fetchWeatherForDate(lat: Double, lon: Double, date: LocalDate): List<HourlyWeather> {
        val dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val response = openMeteoApi.getWeatherForDateRange(lat, lon, dateStr, dateStr)
        return WeatherMapper.extractAllHours(response)
    }

    suspend fun saveSnapshot(snapshot: WeatherSnapshot): Long {
        return weatherSnapshotDao.insert(snapshot)
    }

    suspend fun getAllSnapshots(): List<WeatherSnapshot> {
        return weatherSnapshotDao.getAll()
    }

    suspend fun deleteAllSnapshots() {
        weatherSnapshotDao.deleteAll()
    }
}
