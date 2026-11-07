package com.example.perfectoutfit.feature.rate

import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ItemsToOfferTest {

    private val jersey = ClothingItem(id = 1, bodyPart = BodyPart.UPPER_BODY, name = "Jersey")
    private val vest = ClothingItem(id = 2, bodyPart = BodyPart.UPPER_BODY, name = "Vest")
    private val socks = ClothingItem(id = 3, bodyPart = BodyPart.FEET, name = "Socks")
    private val fleece = ClothingItem(id = 4, bodyPart = BodyPart.UPPER_BODY, name = "Fleece")

    @Test
    fun `new outfits offer the catalog head to toe`() {
        assertEquals(listOf(jersey, vest, socks), itemsToOffer(listOf(socks, jersey, vest), emptyList()))
    }

    @Test
    fun `items on the entry are offered once, even when also in the catalog`() {
        assertEquals(listOf(jersey, vest, socks), itemsToOffer(listOf(socks, jersey, vest), listOf(jersey)))
    }

    @Test
    fun `an entry item no longer tagged with the activity type sits among its body part peers`() {
        assertEquals(
            listOf(fleece, jersey, vest, socks),
            itemsToOffer(listOf(socks, jersey, vest), listOf(jersey, fleece))
        )
    }
}
