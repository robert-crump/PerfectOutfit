package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "weather_snapshots")
data class WeatherSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    override val temperatureCelsius: Double,
    override val apparentTemperatureCelsius: Double,
    override val windSpeedKmh: Double,
    override val windDirectionDegrees: Int,
    override val uvIndex: Int,
    override val cloudCoverPercent: Int,
    override val precipitationProbabilityPercent: Int
) : WeatherReading
