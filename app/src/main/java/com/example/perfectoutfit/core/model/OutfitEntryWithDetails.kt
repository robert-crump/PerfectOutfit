package com.example.perfectoutfit.core.model

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class OutfitEntryWithDetails(
    @Embedded val entry: OutfitEntry,
    @Relation(
        parentColumn = "weatherSnapshotId",
        entityColumn = "id"
    )
    val weatherSnapshot: WeatherSnapshot,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = OutfitItem::class,
            parentColumn = "outfitEntryId",
            entityColumn = "clothingItemId"
        )
    )
    val clothingItems: List<ClothingItem>
)
