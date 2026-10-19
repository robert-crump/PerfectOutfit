package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** A clothing item; the activity types it belongs to live in [ClothingItemActivityType]. */
@Serializable
@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyPart: BodyPart,
    val name: String,
    val isDefault: Boolean = false
)
