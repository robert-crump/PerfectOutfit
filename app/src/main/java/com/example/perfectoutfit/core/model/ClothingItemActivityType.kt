package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import kotlinx.serialization.Serializable

/** One activity type a clothing item belongs to; an item has one row per activity type. */
@Serializable
@Entity(
    tableName = "clothing_item_activity_types",
    primaryKeys = ["clothingItemId", "activityType"],
    foreignKeys = [
        ForeignKey(
            entity = ClothingItem::class,
            parentColumns = ["id"],
            childColumns = ["clothingItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("activityType")]
)
data class ClothingItemActivityType(
    val clothingItemId: Long,
    val activityType: ActivityType
)
