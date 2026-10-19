package com.example.perfectoutfit.feature.catalog

import com.example.perfectoutfit.core.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteConfirmationMessageTest {

    @Test
    fun `names the other activity types and the affected entries`() {
        assertEquals(
            "“Jersey” is also used for Cycling and Running, and appears in 37 outfit entries. Delete it everywhere?",
            deleteConfirmationMessage(listOf("Jersey"), listOf(ActivityType.CYCLING, ActivityType.RUNNING), 37)
        )
    }

    @Test
    fun `an item of the viewed activity type only mentions its entries`() {
        assertEquals(
            "“Jersey” appears in 1 outfit entry. Delete it everywhere?",
            deleteConfirmationMessage(listOf("Jersey"), emptyList(), 1)
        )
    }

    @Test
    fun `an unused item is simply deleted`() {
        assertEquals(
            "“Jersey” appears in no outfit entries. Delete it?",
            deleteConfirmationMessage(listOf("Jersey"), emptyList(), 0)
        )
    }

    @Test
    fun `several items`() {
        assertEquals(
            "The 2 selected items are also used for Running, and appear in no outfit entries. Delete them everywhere?",
            deleteConfirmationMessage(listOf("Jersey", "Vest"), listOf(ActivityType.RUNNING), 0)
        )
    }
}
