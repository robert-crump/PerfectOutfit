package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_snapshots")
data class WeatherSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val windSpeedKmh: Double,
    val windDirectionDegrees: Int,
    val uvIndex: Int,
    val cloudCoverPercent: Int,
    val precipitationProbabilityPercent: Int
)
