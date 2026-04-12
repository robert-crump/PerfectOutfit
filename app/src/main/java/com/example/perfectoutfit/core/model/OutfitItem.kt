package com.example.perfectoutfit.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "outfit_items",
    primaryKeys = ["outfitEntryId", "clothingItemId"],
    foreignKeys = [
        ForeignKey(
            entity = OutfitEntry::class,
            parentColumns = ["id"],
            childColumns = ["outfitEntryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ClothingItem::class,
            parentColumns = ["id"],
            childColumns = ["clothingItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clothingItemId")]
)
data class OutfitItem(
    val outfitEntryId: Long,
    val clothingItemId: Long
)
