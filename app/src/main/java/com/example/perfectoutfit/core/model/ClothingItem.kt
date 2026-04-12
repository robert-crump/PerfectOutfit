package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clothing_items")
data class ClothingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sport: Sport,
    val bodyPart: BodyPart,
    val name: String,
    val isDefault: Boolean = false
)
