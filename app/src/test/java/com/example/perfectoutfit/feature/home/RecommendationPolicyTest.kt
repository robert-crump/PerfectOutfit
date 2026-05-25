package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecommendationPolicyTest {

    // ── Builders ─────────────────────────────────────────────────────────────

    private fun snapshot(
        apparent: Double,
        real: Double = apparent,
        time: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0)
    ) = WeatherSnapshot(
        id = 0,
        timestamp = time.atZone(ZoneId.systemDefault()).toEpochSecond() * 1000,
        latitude = 0.0,
        longitude = 0.0,
        locationName = "",
        temperatureCelsius = real,
        apparentTemperatureCelsius = apparent,
        windSpeedKmh = 0.0,
        windDirectionDegrees = 0,
        uvIndex = 0,
        cloudCoverPercent = 0,
        precipitationProbabilityPercent = 0
    )

    private fun entry(
        id: Long,
        rating: Int?,
        apparent: Double,
        real: Double = apparent,
        time: LocalDateTime = LocalDateTime.of(2026, 1, 1, 12, 0),
        items: List<ClothingItem> = emptyList()
    ) = OutfitEntryWithDetails(
        entry = OutfitEntry(
            id = id,
            weatherSnapshotId = id,
            sport = Sport.CYCLING,
            comfortRating = rating,
            createdAt = 0
        ),
        weatherSnapshot = snapshot(apparent = apparent, real = real, time = time),
        clothingItems = items
    )

    private fun item(id: Long) = ClothingItem(
        id = id, sport = Sport.CYCLING, bodyPart = BodyPart.HEAD_THROAT, name = "item$id"
    )

    private val t = LocalDateTime.of(2026, 1, 1, 12, 0)
    private val older = t.minusHours(1)
    private val newest = t.plusHours(1)

    // ── findRecommendation ────────────────────────────────────────────────────

    @Test
    fun `returns null for empty candidates`() {
        assertNull(RecommendationPolicy.findRecommendation(emptyList(), 10, true))
    }

    @Test
    fun `returns null when all entries are unrated`() {
        val candidates = listOf(entry(1, rating = null, apparent = 10.0))
        assertNull(RecommendationPolicy.findRecommendation(candidates, 10, true))
    }

    @Test
    fun `exact match returns the only rated entry`() {
        val candidates = listOf(entry(1, rating = 0, apparent = 10.0))
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertEquals(1L, result?.entry?.id)
    }

    @Test
    fun `exact match prefers newest when multiple exact matches exist`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older),
            entry(2, rating = 0, apparent = 10.0, time = newest)
        )
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `exact match ignores rating when choosing newest`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older),   // perfect but older
            entry(2, rating = -1, apparent = 10.0, time = newest)  // too cold but newer
        )
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `falls through to plus-minus-1 when no exact match`() {
        val candidates = listOf(entry(1, rating = 0, apparent = 9.0))
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertEquals(1L, result?.entry?.id)
    }

    @Test
    fun `plus-minus-1 prefers perfect over too-hot`() {
        val candidates = listOf(
            entry(1, rating = 1, apparent = 9.0, time = newest),  // too hot, newer
            entry(2, rating = 0, apparent = 11.0, time = older)   // perfect, older
        )
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `plus-minus-1 prefers too-hot over too-cold`() {
        val candidates = listOf(
            entry(1, rating = -1, apparent = 9.0, time = newest), // too cold, newer
            entry(2, rating = 1, apparent = 11.0, time = older)   // too hot, older
        )
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `plus-minus-1 breaks rating ties by newest timestamp`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 9.0, time = older),
            entry(2, rating = 0, apparent = 11.0, time = newest)
        )
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `falls through to plus-minus-2 when no match in plus-minus-1`() {
        val candidates = listOf(entry(1, rating = 0, apparent = 8.0))
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertEquals(1L, result?.entry?.id)
    }

    @Test
    fun `returns null when no candidates within plus-minus-2`() {
        val candidates = listOf(entry(1, rating = 0, apparent = 7.0))
        val result = RecommendationPolicy.findRecommendation(candidates, 10, true)
        assertNull(result)
    }

    @Test
    fun `useApparent false uses real temperature`() {
        // apparent=10 would match target 10; real=20 is outside ±2 of 10
        val candidates = listOf(entry(1, rating = 0, apparent = 10.0, real = 20.0))
        assertNull(RecommendationPolicy.findRecommendation(candidates, 10, useApparent = false))
        assertEquals(1L, RecommendationPolicy.findRecommendation(candidates, 20, useApparent = false)?.entry?.id)
    }

    @Test
    fun `temperature rounding at half-degree boundary`() {
        // 9.5 rounds to 10 → exact match at target 10
        val candidates = listOf(entry(1, rating = 0, apparent = 9.5))
        assertEquals(1L, RecommendationPolicy.findRecommendation(candidates, 10, true)?.entry?.id)
    }

    // ── likelyItemIds ─────────────────────────────────────────────────────────

    @Test
    fun `likelyItemIds returns empty set for empty candidates`() {
        assertEquals(emptySet<Long>(), RecommendationPolicy.likelyItemIds(emptyList(), 10, true))
    }

    @Test
    fun `likelyItemIds includes items from perfect-rated entries within plus-minus-2`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, items = listOf(item(101), item(102)))
        )
        assertEquals(setOf(101L, 102L), RecommendationPolicy.likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds excludes entries with non-perfect rating`() {
        val candidates = listOf(
            entry(1, rating = 1, apparent = 10.0, items = listOf(item(101))),
            entry(2, rating = -1, apparent = 10.0, items = listOf(item(102)))
        )
        assertEquals(emptySet<Long>(), RecommendationPolicy.likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds excludes entries outside plus-minus-2`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 7.0, items = listOf(item(101))),  // 7 < 8 = 10-2, outside
            entry(2, rating = 0, apparent = 13.0, items = listOf(item(102)))  // 13 > 12 = 10+2, outside
        )
        assertEquals(emptySet<Long>(), RecommendationPolicy.likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds includes edge of plus-minus-2 range`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 8.0, items = listOf(item(101))),  // exactly -2
            entry(2, rating = 0, apparent = 12.0, items = listOf(item(102)))  // exactly +2
        )
        assertEquals(setOf(101L, 102L), RecommendationPolicy.likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds deduplicates clothing items appearing in multiple entries`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 9.0, items = listOf(item(101))),
            entry(2, rating = 0, apparent = 11.0, items = listOf(item(101), item(102)))
        )
        assertEquals(setOf(101L, 102L), RecommendationPolicy.likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds useApparent false uses real temperature`() {
        // apparent=10 would be in range at target 10; real=20 is not
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, real = 20.0, items = listOf(item(101)))
        )
        assertEquals(emptySet<Long>(), RecommendationPolicy.likelyItemIds(candidates, 10, useApparent = false))
        assertEquals(setOf(101L), RecommendationPolicy.likelyItemIds(candidates, 20, useApparent = false))
    }
}
