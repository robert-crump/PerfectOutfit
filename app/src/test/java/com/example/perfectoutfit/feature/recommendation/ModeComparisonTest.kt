package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModeComparisonTest {

    private val dao = FakeOutfitEntryDao()
    private val recommendations = Recommendations(dao)

    private fun compare(vararg entries: OutfitEntryWithDetails): ModeComparison {
        dao.details.value = entries.toList()
        return runBlocking { recommendations.modeComparison().first() }
    }

    // ── Builders ─────────────────────────────────────────────────────────────

    private var nextId = 1L

    private fun entry(
        rating: Int?,
        actual: Double,
        feelsLike: Double = actual,
        items: List<Long> = listOf(1L),
        activityType: ActivityType = ActivityType.CYCLING
    ): OutfitEntryWithDetails {
        val id = nextId++
        return OutfitEntryWithDetails(
            entry = OutfitEntry(
                id = id,
                weatherSnapshotId = id,
                activityType = activityType,
                comfortRating = rating,
                createdAt = 0
            ),
            weatherSnapshot = WeatherSnapshot(
                id = id,
                timestamp = 0,
                latitude = 0.0,
                longitude = 0.0,
                locationName = "",
                temperatureCelsius = actual,
                apparentTemperatureCelsius = feelsLike,
                windSpeedKmh = 0.0,
                windDirectionDegrees = 0,
                uvIndex = 0,
                cloudCoverPercent = 0,
                precipitationProbabilityPercent = 0
            ),
            clothingItems = items.map { ClothingItem(id = it, bodyPart = BodyPart.HEAD_THROAT, name = "item$it") }
        )
    }

    private val ModeComparison.cycling get() = byActivityType.getValue(ActivityType.CYCLING)

    // ── Pairs ────────────────────────────────────────────────────────────────

    @Test
    fun `no entries gives no pairs and no scores`() {
        val line = compare().overall
        assertEquals(0, line.pairs)
        assertNull(line.feelsLikeScore)
        assertNull(line.actualScore)
    }

    @Test
    fun `warmer entry rated warmer is consistent`() {
        val line = compare(
            entry(rating = -1, actual = 5.0),
            entry(rating = 0, actual = 9.0)
        ).cycling
        assertEquals(ModeComparisonLine(pairs = 1, feelsLikeHalves = 0, actualHalves = 0), line)
    }

    @Test
    fun `warmer entry rated colder is contradictory`() {
        val line = compare(
            entry(rating = -1, actual = 9.0),
            entry(rating = 0, actual = 5.0)
        ).cycling
        assertEquals(ModeComparisonLine(pairs = 1, feelsLikeHalves = 2, actualHalves = 2), line)
        assertEquals(1.0, line.actualScore!!, 0.0)
    }

    @Test
    fun `same rounded temperature is a tie counting half`() {
        // Actual 6° vs 9° tells them apart; feels like 4.4° and 3.6° both round to 4°.
        val line = compare(
            entry(rating = -1, actual = 6.0, feelsLike = 4.4),
            entry(rating = 0, actual = 9.0, feelsLike = 3.6)
        ).cycling
        assertEquals(ModeComparisonLine(pairs = 1, feelsLikeHalves = 1, actualHalves = 0), line)
        assertEquals(0.5, line.feelsLikeScore!!, 0.0)
        assertEquals(0.0, line.actualScore!!, 0.0)
    }

    @Test
    fun `too hot ranks above perfect`() {
        val line = compare(
            entry(rating = 1, actual = 15.0),
            entry(rating = 0, actual = 10.0)
        ).cycling
        assertEquals(0, line.actualHalves)
    }

    @Test
    fun `same rating and unrated entries make no pair`() {
        val line = compare(
            entry(rating = 0, actual = 3.0),
            entry(rating = 0, actual = 9.0),
            entry(rating = null, actual = 20.0)
        ).cycling
        assertEquals(0, line.pairs)
    }

    @Test
    fun `different item sets make no pair`() {
        val line = compare(
            entry(rating = -1, actual = 9.0, items = listOf(1L, 2L)),
            entry(rating = 0, actual = 5.0, items = listOf(1L))
        ).cycling
        assertEquals(0, line.pairs)
    }

    @Test
    fun `item order does not matter`() {
        val line = compare(
            entry(rating = -1, actual = 5.0, items = listOf(1L, 2L)),
            entry(rating = 0, actual = 9.0, items = listOf(2L, 1L))
        ).cycling
        assertEquals(1, line.pairs)
    }

    @Test
    fun `entries without items make no pair`() {
        val line = compare(
            entry(rating = -1, actual = 5.0, items = emptyList()),
            entry(rating = 0, actual = 9.0, items = emptyList())
        ).cycling
        assertEquals(0, line.pairs)
    }

    @Test
    fun `pairs never cross activity types`() {
        val comparison = compare(
            entry(rating = -1, actual = 9.0, activityType = ActivityType.WALKING),
            entry(rating = 0, actual = 5.0, activityType = ActivityType.BIKE_COMMUTE)
        )
        assertEquals(0, comparison.overall.pairs)
    }

    @Test
    fun `overall pools pairs of all activity types`() {
        val comparison = compare(
            entry(rating = -1, actual = 5.0, activityType = ActivityType.WALKING),
            entry(rating = 0, actual = 9.0, activityType = ActivityType.WALKING),
            entry(rating = -1, actual = 9.0, activityType = ActivityType.RUNNING),
            entry(rating = 0, actual = 5.0, activityType = ActivityType.RUNNING),
            entry(rating = 1, actual = 4.0, activityType = ActivityType.RUNNING)
        )
        // Running: (-1@9, 0@5) contradicts, (-1@9, 1@4) contradicts, (0@5, 1@4) contradicts.
        assertEquals(ModeComparisonLine(pairs = 3, feelsLikeHalves = 6, actualHalves = 6), comparison.byActivityType[ActivityType.RUNNING])
        assertEquals(ModeComparisonLine(pairs = 4, feelsLikeHalves = 6, actualHalves = 6), comparison.overall)
        assertEquals(ModeComparisonLine.EMPTY, comparison.byActivityType[ActivityType.CYCLING])
    }

    // ── Verdict ──────────────────────────────────────────────────────────────

    @Test
    fun `fewer than 20 pairs is not enough data`() {
        assertEquals(ModeVerdict.NOT_ENOUGH_DATA, ModeComparisonLine(pairs = 19, feelsLikeHalves = 0, actualHalves = 38).verdict)
    }

    @Test
    fun `a gap under 10 points is no clear difference`() {
        // 20 pairs: 3.5 vs 2 contradictions is 17.5% vs 10%, 7.5 points apart.
        assertEquals(ModeVerdict.NO_CLEAR_DIFFERENCE, ModeComparisonLine(pairs = 20, feelsLikeHalves = 7, actualHalves = 4).verdict)
    }

    @Test
    fun `a gap of exactly 10 points gives a verdict for the lower score`() {
        assertEquals(ModeVerdict.ACTUAL_BETTER, ModeComparisonLine(pairs = 20, feelsLikeHalves = 8, actualHalves = 4).verdict)
        assertEquals(ModeVerdict.FEELS_LIKE_BETTER, ModeComparisonLine(pairs = 20, feelsLikeHalves = 4, actualHalves = 8).verdict)
    }
}
