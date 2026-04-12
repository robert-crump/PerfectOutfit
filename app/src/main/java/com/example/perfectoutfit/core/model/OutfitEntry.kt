package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val sport: Sport,
    val comfortRating: Int? = null,
    val createdAt: Long,
    val ratedAt: Long? = null,
    val notes: String = ""
) {
    /** True when the user has assigned a rating (-1 / 0 / 1); false for "No rating". */
    val bHasRating: Boolean get() = comfortRating != null
}
