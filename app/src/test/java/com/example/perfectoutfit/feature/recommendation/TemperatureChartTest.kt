package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ClothingItemActivityType
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Explorer's temperature chart: strip, item rows, grouping and the tag filter. */
class TemperatureChartTest {

    private val jacket = ClothingItem(id = 1, bodyPart = BodyPart.UPPER_BODY, name = "Jacket")
    private val jersey = ClothingItem(id = 2, bodyPart = BodyPart.UPPER_BODY, name = "Jersey")
    private val tights = ClothingItem(id = 3, bodyPart = BodyPart.LEGS, name = "Tights")
    private val gloves = ClothingItem(id = 4, bodyPart = BodyPart.HANDS, name = "Gloves")
    private val vest = ClothingItem(id = 5, bodyPart = BodyPart.UPPER_BODY, name = "Vest")

    private fun entry(
        id: Long,
        rating: Int?,
        apparent: Double,
        vararg items: ClothingItem,
        real: Double = apparent,
        activityType: ActivityType = ActivityType.CYCLING
    ) = OutfitEntryWithDetails(
        entry = OutfitEntry(
            id = id, weatherSnapshotId = id, activityType = activityType,
            comfortRating = rating, createdAt = id
        ),
        weatherSnapshot = WeatherSnapshot(
            id = id, timestamp = id, latitude = 0.0, longitude = 0.0, locationName = "",
            temperatureCelsius = real, apparentTemperatureCelsius = apparent,
            windSpeedKmh = 0.0, windDirectionDegrees = 0, uvIndex = 0,
            cloudCoverPercent = 0, precipitationProbabilityPercent = 0
        ),
        clothingItems = items.toList()
    )

    private fun build(
        entries: List<OutfitEntryWithDetails>,
        tagged: List<ClothingItem> = listOf(jacket, jersey, tights, gloves, vest),
        useApparent: Boolean = true
    ) = TemperatureChartPolicy.build(entries, tagged, useApparent)

    // ── strip ─────────────────────────────────────────────────────────────────

    @Test
    fun `strip spans coldest to warmest rated degree with three states`() {
        val chart = build(listOf(
            entry(1, rating = 0, apparent = 2.4, jacket),
            entry(2, rating = 0, apparent = 1.6, tights),   // rounds to 2 → second perfect outfit
            entry(3, rating = -1, apparent = 4.0, jersey),
            entry(4, rating = 1, apparent = 5.0, vest),
            entry(5, rating = null, apparent = -10.0, jacket) // unrated → ignored
        ))
        assertEquals(
            listOf(
                DegreeCell(2, DegreeCoverage.PERFECT, 2),
                DegreeCell(3, DegreeCoverage.NONE, 0),
                DegreeCell(4, DegreeCoverage.RATED, 0),
                DegreeCell(5, DegreeCoverage.RATED, 0)
            ),
            chart.degrees
        )
        assertEquals(DegreeCoverage.RATED, chart.cellAt(4)?.coverage)
        assertEquals(null, chart.cellAt(6))
    }

    @Test
    fun `strip counts perfect outfits whatever their items are tagged for`() {
        val chart = build(listOf(entry(1, rating = 0, apparent = 7.0, jacket)), tagged = emptyList())
        assertEquals(listOf(DegreeCell(7, DegreeCoverage.PERFECT, 1)), chart.degrees)
        assertTrue(chart.groups.isEmpty())
    }

    @Test
    fun `no rated history means an empty chart`() {
        val chart = build(listOf(entry(1, rating = null, apparent = 7.0, jacket)))
        assertFalse(chart.hasRatedHistory)
        assertEquals(null, chart.minTemp)
        assertTrue(chart.groups.isEmpty())
    }

    // ── item rows ─────────────────────────────────────────────────────────────

    @Test
    fun `item row holds the distinct perfect degrees only`() {
        val chart = build(listOf(
            entry(1, rating = 0, apparent = 2.0, jacket),
            entry(2, rating = 0, apparent = 18.0, jacket),
            entry(3, rating = 0, apparent = 18.2, jacket),  // duplicate degree 18
            entry(4, rating = -1, apparent = 10.0, jacket)  // not perfect → not on the row
        ))
        val row = chart.groups.single().rows.single()
        assertEquals(listOf(2, 18), row.perfectTemps)
        assertTrue(row.spans(10))
        assertFalse(row.spans(19))
    }

    @Test
    fun `only tagged items with a perfect entry get a row`() {
        val chart = build(
            listOf(entry(1, rating = 0, apparent = 5.0, jacket, gloves)),
            tagged = listOf(jacket, tights)
        )
        assertEquals(listOf(jacket), chart.groups.flatMap { g -> g.rows.map { it.item } })
    }

    @Test
    fun `rows are grouped by body part display order and sorted by min then max`() {
        val chart = build(listOf(
            entry(1, rating = 0, apparent = 10.0, jersey, tights, gloves),
            entry(2, rating = 0, apparent = 0.0, jacket),
            entry(3, rating = 0, apparent = 4.0, jacket, vest),
            entry(4, rating = 0, apparent = 0.0, vest),
            entry(5, rating = 0, apparent = 12.0, vest)
        ))
        assertEquals(listOf(BodyPart.LEGS, BodyPart.UPPER_BODY, BodyPart.HANDS), chart.groups.map { it.bodyPart })
        // Jacket 0..4 before Vest 0..12 (same min, lower max), then Jersey 10..10.
        assertEquals(listOf(jacket, vest, jersey), chart.groups[1].rows.map { it.item })
    }

    @Test
    fun `chart follows the apparent-vs-real preference`() {
        val entries = listOf(entry(1, rating = 0, apparent = 5.0, jacket, real = 15.0))
        assertEquals(listOf(5), build(entries, useApparent = true).groups.single().rows.single().perfectTemps)
        assertEquals(15, build(entries, useApparent = false).minTemp)
    }

    // ── flow ──────────────────────────────────────────────────────────────────

    @Test
    fun `temperatureChart combines the activity type's entries with its tagged items`() = runBlocking {
        val entryDao = FakeOutfitEntryDao()
        val itemDao = FakeClothingItemDao()
        itemDao.insertAll(listOf(jacket, tights))
        itemDao.insertActivityTypes(listOf(
            ClothingItemActivityType(jacket.id, ActivityType.CYCLING),
            ClothingItemActivityType(tights.id, ActivityType.RUNNING)
        ))
        entryDao.details.value = listOf(
            entry(1, rating = 0, apparent = 3.0, jacket, tights),
            entry(2, rating = 0, apparent = 20.0, jacket, activityType = ActivityType.RUNNING)
        )
        val chart = Recommendations(entryDao, itemDao).temperatureChart(ActivityType.CYCLING, true).first()
        assertEquals(listOf(DegreeCell(3, DegreeCoverage.PERFECT, 1)), chart.degrees)
        assertEquals(listOf(jacket), chart.groups.flatMap { g -> g.rows.map { it.item } })
    }
}
