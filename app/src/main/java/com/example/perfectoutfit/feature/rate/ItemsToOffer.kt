package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.inHeadToToeOrder

/**
 * The items an outfit can be built from: the activity type's catalog plus any item already on
 * the entry, so an item later untagged from that activity type is never hidden or silently
 * dropped on save.
 */
internal fun itemsToOffer(catalogItems: List<ClothingItem>, entryItems: List<ClothingItem>): List<ClothingItem> {
    val catalogIds = catalogItems.mapTo(HashSet()) { it.id }
    val extra = entryItems.filter { it.id !in catalogIds }
    // Head to toe, so extras sit among their peers.
    return (catalogItems + extra).inHeadToToeOrder()
}
