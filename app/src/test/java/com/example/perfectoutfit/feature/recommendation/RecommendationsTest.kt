package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecommendationsTest {

    private val dao = FakeOutfitEntryDao()
    private val recommendations = Recommendations(dao, FakeClothingItemDao())

    private fun findRecommendation(
        candidates: List<OutfitEntryWithDetails>,
        target: Int,
        useApparent: Boolean
    ): OutfitEntryWithDetails? {
        dao.ratedEntries = candidates
        return runBlocking { recommendations.find(ActivityType.CYCLING, target.toDouble(), useApparent) }
    }

    private fun likelyItemIds(
        candidates: List<OutfitEntryWithDetails>,
        target: Int,
        useApparent: Boolean
    ): Set<Long> {
        dao.ratedEntries = candidates
        return runBlocking { recommendations.likelyItemIds(ActivityType.CYCLING, target.toDouble(), useApparent) }
    }

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
            activityType = ActivityType.CYCLING,
            comfortRating = rating,
            createdAt = 0
        ),
        weatherSnapshot = snapshot(apparent = apparent, real = real, time = time),
        clothingItems = items
    )

    private fun item(id: Long) = ClothingItem(
        id = id, bodyPart = BodyPart.HEAD_THROAT, name = "item$id"
    )

    private val t = LocalDateTime.of(2026, 1, 1, 12, 0)
    private val older = t.minusHours(1)
    private val newest = t.plusHours(1)

    // ── findRecommendation ────────────────────────────────────────────────────

    @Test
    fun `returns null for empty candidates`() {
        assertNull(findRecommendation(emptyList(), 10, true))
    }

    @Test
    fun `returns null when all entries are unrated`() {
        val candidates = listOf(entry(1, rating = null, apparent = 10.0))
        assertNull(findRecommendation(candidates, 10, true))
    }

    @Test
    fun `exact match returns the only rated entry`() {
        val candidates = listOf(entry(1, rating = 0, apparent = 10.0))
        val result = findRecommendation(candidates, 10, true)
        assertEquals(1L, result?.entry?.id)
    }

    @Test
    fun `exact match prefers newest when multiple exact matches exist`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older),
            entry(2, rating = 0, apparent = 10.0, time = newest)
        )
        val result = findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `exact match prefers perfect over a newer too-cold`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older, items = listOf(item(101))),
            entry(2, rating = -1, apparent = 10.0, time = newest, items = listOf(item(102)))
        )
        assertEquals(1L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `perfect within plus-minus-2 beats a non-perfect exact match`() {
        val candidates = listOf(
            entry(1, rating = 1, apparent = 10.0, time = newest),
            entry(2, rating = 0, apparent = 12.0, time = older)
        )
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `closest perfect beats a newer perfect farther away`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 11.0, time = older),
            entry(2, rating = 0, apparent = 12.0, time = newest)
        )
        assertEquals(1L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    // ── Repeated combinations ────────────────────────────────────────────────

    @Test
    fun `repeated perfect combination beats a single newer perfect at the same distance`() {
        val x = listOf(item(101), item(102))
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older.minusHours(1), items = x),
            entry(2, rating = 0, apparent = 10.0, time = older, items = x),
            entry(3, rating = 0, apparent = 10.0, time = newest, items = listOf(item(103)))
        )
        // the newest entry of the winning combination is shown
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `repeat count counts the combination across the whole window`() {
        val x = listOf(item(101))
        val candidates = listOf(
            entry(1, rating = 0, apparent = 12.0, time = older, items = x),
            entry(2, rating = 0, apparent = 9.0, time = older.minusHours(1), items = x),
            entry(3, rating = 0, apparent = 11.0, time = newest, items = listOf(item(103)))
        )
        // x's closest perfect (9°) ties with item 103 on distance; x wins on count, shown via its closest entry
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `repeat count does not outweigh closeness`() {
        val x = listOf(item(101))
        val candidates = listOf(
            entry(1, rating = 0, apparent = 12.0, time = older, items = x),
            entry(2, rating = 0, apparent = 12.0, time = older, items = x),
            entry(3, rating = 0, apparent = 10.0, time = older.minusHours(5), items = listOf(item(103)))
        )
        assertEquals(3L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `subset of items is a different combination`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older, items = listOf(item(101))),
            entry(2, rating = 0, apparent = 10.0, time = older, items = listOf(item(101), item(102))),
            entry(3, rating = 0, apparent = 10.0, time = newest, items = listOf(item(103)))
        )
        assertEquals(3L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `entries without items never count as a repeated combination`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older),
            entry(2, rating = 0, apparent = 10.0, time = older),
            entry(3, rating = 0, apparent = 10.0, time = newest, items = listOf(item(103)))
        )
        assertEquals(3L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    // ── Cancelling ────────────────────────────────────────────────────────────

    @Test
    fun `newer known-wrong rating cancels the combination's perfect entries`() {
        val x = listOf(item(101))
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older, items = x),
            entry(2, rating = -1, apparent = 11.0, time = newest, items = x),   // too cold above target
            entry(3, rating = 0, apparent = 12.0, time = older, items = listOf(item(103)))
        )
        assertEquals(3L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `cancelled combination can still be the fallback`() {
        val x = listOf(item(101))
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older, items = x),
            entry(2, rating = 1, apparent = 9.0, time = newest, items = x)      // too hot below target
        )
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `newer rating pointing toward today does not cancel`() {
        val x = listOf(item(101))
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older, items = x),
            entry(2, rating = 1, apparent = 12.0, time = newest, items = x),    // too hot when warmer: agrees
            entry(3, rating = 0, apparent = 11.0, time = newest, items = listOf(item(103)))
        )
        assertEquals(1L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `older known-wrong rating does not cancel`() {
        val x = listOf(item(101))
        val candidates = listOf(
            entry(1, rating = -1, apparent = 10.0, time = older, items = x),
            entry(2, rating = 0, apparent = 10.0, time = newest, items = x)
        )
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `known-wrong rating of another combination does not cancel`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older, items = listOf(item(101))),
            entry(2, rating = -1, apparent = 10.0, time = newest, items = listOf(item(101), item(102)))
        )
        assertEquals(1L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `known-wrong rating outside the window does not cancel`() {
        val x = listOf(item(101))
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older, items = x),
            entry(2, rating = -1, apparent = 15.0, time = newest, items = x)
        )
        assertEquals(1L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    // ── Fallback (no perfect) ─────────────────────────────────────────────────

    @Test
    fun `fallback prefers too-hot worn warmer over an exact too-cold`() {
        val candidates = listOf(
            entry(1, rating = -1, apparent = 10.0, time = newest),
            entry(2, rating = 1, apparent = 12.0, time = older)
        )
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `fallback prefers too-cold worn colder over a closer too-hot worn colder`() {
        val candidates = listOf(
            entry(1, rating = 1, apparent = 9.0, time = newest),
            entry(2, rating = -1, apparent = 8.0, time = older)
        )
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `fallback treats a non-perfect exact match as known wrong`() {
        val candidates = listOf(
            entry(1, rating = 1, apparent = 10.0, time = newest),
            entry(2, rating = 1, apparent = 12.0, time = older)
        )
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `fallback within a group prefers closest`() {
        val candidates = listOf(
            entry(1, rating = 1, apparent = 12.0, time = newest),
            entry(2, rating = -1, apparent = 9.0, time = older)
        )
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `fallback with only known-wrong entries still returns the best of them`() {
        val candidates = listOf(
            entry(1, rating = -1, apparent = 11.0, time = newest),
            entry(2, rating = 1, apparent = 9.0, time = older)
        )
        // both 1° away and known wrong: too hot beats too cold
        assertEquals(2L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    @Test
    fun `matches within plus-minus-1`() {
        val candidates = listOf(entry(1, rating = 0, apparent = 9.0))
        val result = findRecommendation(candidates, 10, true)
        assertEquals(1L, result?.entry?.id)
    }

    @Test
    fun `perfect beats a newer too-hot`() {
        val candidates = listOf(
            entry(1, rating = 1, apparent = 9.0, time = newest),  // too hot, newer
            entry(2, rating = 0, apparent = 11.0, time = older)   // perfect, older
        )
        val result = findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `fallback at equal distance prefers too-hot over too-cold`() {
        val candidates = listOf(
            entry(1, rating = -1, apparent = 9.0, time = newest), // too cold, newer
            entry(2, rating = 1, apparent = 11.0, time = older)   // too hot, older
        )
        val result = findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `perfect at equal distance breaks ties by newest`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 9.0, time = older),
            entry(2, rating = 0, apparent = 11.0, time = newest)
        )
        val result = findRecommendation(candidates, 10, true)
        assertEquals(2L, result?.entry?.id)
    }

    @Test
    fun `matches within plus-minus-2`() {
        val candidates = listOf(entry(1, rating = 0, apparent = 8.0))
        val result = findRecommendation(candidates, 10, true)
        assertEquals(1L, result?.entry?.id)
    }

    @Test
    fun `returns null when no candidates within plus-minus-2`() {
        val candidates = listOf(entry(1, rating = 0, apparent = 7.0))
        val result = findRecommendation(candidates, 10, true)
        assertNull(result)
    }

    @Test
    fun `useApparent false uses real temperature`() {
        // apparent=10 would match target 10; real=20 is outside ±2 of 10
        val candidates = listOf(entry(1, rating = 0, apparent = 10.0, real = 20.0))
        assertNull(findRecommendation(candidates, 10, useApparent = false))
        assertEquals(1L, findRecommendation(candidates, 20, useApparent = false)?.entry?.id)
    }

    @Test
    fun `temperature rounding at half-degree boundary`() {
        // 9.5 rounds to 10 → exact match at target 10
        val candidates = listOf(entry(1, rating = 0, apparent = 9.5))
        assertEquals(1L, findRecommendation(candidates, 10, true)?.entry?.id)
    }

    // ── likelyItemIds ─────────────────────────────────────────────────────────

    @Test
    fun `likelyItemIds returns empty set for empty candidates`() {
        assertEquals(emptySet<Long>(), likelyItemIds(emptyList(), 10, true))
    }

    @Test
    fun `likelyItemIds includes items from perfect-rated entries within plus-minus-2`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, items = listOf(item(101), item(102)))
        )
        assertEquals(setOf(101L, 102L), likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds excludes entries with non-perfect rating`() {
        val candidates = listOf(
            entry(1, rating = 1, apparent = 10.0, items = listOf(item(101))),
            entry(2, rating = -1, apparent = 10.0, items = listOf(item(102)))
        )
        assertEquals(emptySet<Long>(), likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds excludes entries outside plus-minus-2`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 7.0, items = listOf(item(101))),  // 7 < 8 = 10-2, outside
            entry(2, rating = 0, apparent = 13.0, items = listOf(item(102)))  // 13 > 12 = 10+2, outside
        )
        assertEquals(emptySet<Long>(), likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds includes edge of plus-minus-2 range`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 8.0, items = listOf(item(101))),  // exactly -2
            entry(2, rating = 0, apparent = 12.0, items = listOf(item(102)))  // exactly +2
        )
        assertEquals(setOf(101L, 102L), likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds deduplicates clothing items appearing in multiple entries`() {
        val candidates = listOf(
            entry(1, rating = 0, apparent = 9.0, items = listOf(item(101))),
            entry(2, rating = 0, apparent = 11.0, items = listOf(item(101), item(102)))
        )
        assertEquals(setOf(101L, 102L), likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds excludes cancelled combinations`() {
        val x = listOf(item(101), item(102))
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, time = older, items = x),
            entry(2, rating = -1, apparent = 10.0, time = newest, items = x),
            entry(3, rating = 0, apparent = 11.0, time = older, items = listOf(item(102), item(103)))
        )
        assertEquals(setOf(102L, 103L), likelyItemIds(candidates, 10, true))
    }

    @Test
    fun `likelyItemIds useApparent false uses real temperature`() {
        // apparent=10 would be in range at target 10; real=20 is not
        val candidates = listOf(
            entry(1, rating = 0, apparent = 10.0, real = 20.0, items = listOf(item(101)))
        )
        assertEquals(emptySet<Long>(), likelyItemIds(candidates, 10, useApparent = false))
        assertEquals(setOf(101L), likelyItemIds(candidates, 20, useApparent = false))
    }
}
