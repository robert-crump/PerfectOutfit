package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import kotlinx.serialization.Serializable

/**
 * One hour of a logged workout's weather (issue #54). Every log stores each hour of its window,
 * the entry's snapshot hour included; entries logged before these existed have none. Stored
 * only: matching, the chart and History read the entry's snapshot. No place of its own: a
 * workout's hours come from one fetch at the snapshot's place.
 */
@Serializable
@Entity(
    tableName = "workout_hours",
    primaryKeys = ["outfitEntryId", "timestamp"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntry::class,
            parentColumns = ["id"],
            childColumns = ["outfitEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class WorkoutHour(
    val outfitEntryId: Long,
    /** Epoch millis of the hour's start, in the zone it was logged in (like `createdAt`). */
    val timestamp: Long,
    override val temperatureCelsius: Double,
    override val apparentTemperatureCelsius: Double,
    override val windSpeedKmh: Double,
    override val windDirectionDegrees: Int,
    override val uvIndex: Int,
    override val cloudCoverPercent: Int,
    override val precipitationProbabilityPercent: Int,
    override val precipitationMm: Double? = null
) : WeatherReading
