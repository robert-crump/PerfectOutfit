package com.example.perfectoutfit.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComfortRatingTest {

    @Test
    fun `temperature order is too cold, perfect, too hot`() {
        assertEquals(
            listOf(ComfortRating.TOO_COLD, ComfortRating.PERFECT, ComfortRating.TOO_HOT),
            listOf(ComfortRating.TOO_HOT, ComfortRating.TOO_COLD, ComfortRating.PERFECT).sorted()
        )
    }

    @Test
    fun `matching priority is perfect, then too hot, then too cold`() {
        assertEquals(
            listOf(ComfortRating.PERFECT, ComfortRating.TOO_HOT, ComfortRating.TOO_COLD),
            ComfortRating.entries.sortedBy { it.matchPriority }
        )
    }

    @Test
    fun `stored values round trip and anything else is invalid`() {
        assertEquals(listOf(-1, 0, 1), ComfortRating.entries.map { it.value })
        ComfortRating.entries.forEach { assertEquals(it, ComfortRating.fromValue(it.value)) }
        assertNull(ComfortRating.fromValue(99))
        assertNull(ComfortRating.fromValue(null))
    }
}
