package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "outfit_entries",
    foreignKeys = [
        ForeignKey(
            entity = WeatherSnapshot::class,
            parentColumns = ["id"],
            childColumns = ["weatherSnapshotId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("weatherSnapshotId")]
)
data class OutfitEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weatherSnapshotId: Long,
    val activityType: ActivityType,
    val comfortRating: Int? = null,
    val createdAt: Long,
    /** When the rating was last set or changed; re-saving the same rating keeps it. */
    val ratedAt: Long? = null,
    val notes: String = "",
    /**
     * Wall-clock millis when the entry was saved. For a live log this is about when the
     * activity started, so `ratedAt - loggedAt` hints at how long it lasted (issue #55).
     * Null for entries logged before it was stored.
     */
    val loggedAt: Long? = null,
    /** How the entry was logged; tells whether [loggedAt] is a start time. Null for older entries. */
    val logMode: LogMode? = null,
    /** The workout duration chosen for a live log; null for past logs and older entries. */
    val plannedDurationHours: Int? = null
) {
    /** [comfortRating] (the stored Int) as the domain type; null when unrated. */
    val rating: ComfortRating? get() = ComfortRating.fromValue(comfortRating)
}
