package com.example.perfectoutfit.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class BodyPartOrderTest {
    private fun item(id: Long, part: BodyPart, name: String) = ClothingItem(id = id, bodyPart = part, name = name)

    private val socks = item(1, BodyPart.FEET, "Socks")
    private val shorts = item(2, BodyPart.LEGS, "Shorts")
    private val gloves = item(3, BodyPart.HANDS, "Gloves")
    private val sleeves = item(4, BodyPart.ARMS, "Sleeves")
    private val vest = item(5, BodyPart.UPPER_BODY, "Vest")
    private val jersey = item(6, BodyPart.UPPER_BODY, "Jersey")
    private val cap = item(7, BodyPart.HEAD_THROAT, "Cap")

    @Test
    fun `items run head to toe, hands after arms, then by name`() {
        assertEquals(
            listOf(cap, jersey, vest, sleeves, gloves, shorts, socks),
            listOf(socks, shorts, gloves, sleeves, vest, jersey, cap).inHeadToToeOrder()
        )
    }

    @Test
    fun `groups skip empty body parts unless asked`() {
        val items = listOf(socks, cap)
        assertEquals(listOf(BodyPart.HEAD_THROAT, BodyPart.FEET), items.headToToeGroups { it.bodyPart }.map { it.first })
        assertEquals(
            listOf(
                BodyPart.HEAD_THROAT, BodyPart.UPPER_BODY, BodyPart.ARMS,
                BodyPart.HANDS, BodyPart.LEGS, BodyPart.FEET
            ),
            items.headToToeGroups(includeEmpty = true) { it.bodyPart }.map { it.first }
        )
    }
}
