package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Workout-window extremes, the Explorer stop list, and the in-flight staleness rule. */
class RecommendationsWindowTest {

    private val dao = FakeOutfitEntryDao()
    private val recommendations = Recommendations(dao)
    private val t = LocalDateTime.of(2026, 1, 1, 12, 0)

    private fun hour(real: Double, apparent: Double = real) = HourlyWeather(
        time = t,
        temperatureCelsius = real,
        apparentTemperatureCelsius = apparent,
        windSpeedKmh = 0.0,
        windDirectionDegrees = 0,
        uvIndex = 0,
        cloudCoverPercent = 0,
        precipitationProbabilityPercent = 0
    )

    private fun entry(id: Long, rating: Int?, apparent: Double, real: Double = apparent) =
        OutfitEntryWithDetails(
            entry = OutfitEntry(
                id = id, weatherSnapshotId = id, sport = Sport.CYCLING,
                comfortRating = rating, createdAt = 0
            ),
            weatherSnapshot = WeatherSnapshot(
                id = id, timestamp = id, latitude = 0.0, longitude = 0.0, locationName = "",
                temperatureCelsius = real, apparentTemperatureCelsius = apparent,
                windSpeedKmh = 0.0, windDirectionDegrees = 0, uvIndex = 0,
                cloudCoverPercent = 0, precipitationProbabilityPercent = 0
            ),
            clothingItems = emptyList()
        )

    // ── rounding at the module edge ───────────────────────────────────────────

    @Test
    fun `find rounds a fractional reference temperature before matching`() {
        dao.ratedEntries = listOf(entry(1, rating = 0, apparent = 10.0))
        val result = runBlocking { recommendations.find(Sport.CYCLING, 9.5, true) }
        assertEquals(1L, result?.entry?.id)
    }

    // ── workoutExtremes ───────────────────────────────────────────────────────

    @Test
    fun `workoutExtremes finds coldest and warmest within the window`() {
        val hours = listOf(hour(5.0), hour(2.0), hour(9.0), hour(-20.0)) // last is outside window of 3
        assertEquals(1 to 2, Recommendations.workoutExtremes(hours, 3, useApparent = true))
    }

    @Test
    fun `workoutExtremes tie goes to the later hour for coldest and warmest`() {
        val hours = listOf(hour(2.0), hour(9.0), hour(2.0), hour(9.0))
        assertEquals(2 to 3, Recommendations.workoutExtremes(hours, 4, useApparent = true))
    }

    @Test
    fun `workoutExtremes single hour is both coldest and warmest`() {
        assertEquals(0 to 0, Recommendations.workoutExtremes(listOf(hour(4.0), hour(8.0)), 1, true))
    }

    @Test
    fun `workoutExtremes judges by the preferred basis`() {
        val hours = listOf(hour(real = 10.0, apparent = 0.0), hour(real = 0.0, apparent = 10.0))
        assertEquals(0 to 1, Recommendations.workoutExtremes(hours, 2, useApparent = true))
        assertEquals(1 to 0, Recommendations.workoutExtremes(hours, 2, useApparent = false))
    }

    // ── findForWorkoutWindow ──────────────────────────────────────────────────

    @Test
    fun `findForWorkoutWindow recommends for the coldest and warmest hours`() {
        dao.ratedEntries = listOf(
            entry(1, rating = 0, apparent = 2.0),
            entry(2, rating = 0, apparent = 9.0)
        )
        val result = runBlocking {
            recommendations.findForWorkoutWindow(Sport.CYCLING, listOf(hour(9.0), hour(2.0)), 2, true)
        }!!
        assertEquals(1, result.coldestHourIndex)
        assertEquals(0, result.warmestHourIndex)
        assertEquals(1L, result.coldest?.entry?.id)
        assertEquals(2L, result.warmest?.entry?.id)
    }

    @Test
    fun `findForWorkoutWindow returns null when hours are empty`() {
        assertNull(runBlocking { recommendations.findForWorkoutWindow(Sport.CYCLING, emptyList(), 2, true) })
    }

    @Test
    fun `findForWorkoutWindow discards the result when sport or duration changed mid-query`() {
        dao.ratedEntries = listOf(entry(1, rating = 0, apparent = 5.0))
        var current = true
        dao.onQuery = { current = false } // user switches sport while the query runs
        val result = runBlocking {
            recommendations.findForWorkoutWindow(
                Sport.CYCLING, listOf(hour(5.0)), 1, true, isCurrent = { current }
            )
        }
        assertNull(result)
    }

    @Test
    fun `findForWorkoutWindow keeps the result when nothing changed mid-query`() {
        dao.ratedEntries = listOf(entry(1, rating = 0, apparent = 5.0))
        val result = runBlocking {
            recommendations.findForWorkoutWindow(
                Sport.CYCLING, listOf(hour(5.0)), 1, true, isCurrent = { true }
            )
        }
        assertEquals(1L, result?.coldest?.entry?.id)
    }

    // ── stops (Explorer) ──────────────────────────────────────────────────────

    @Test
    fun `stops are distinct rounded reference temperatures sorted ascending`() {
        dao.ratedEntries = listOf(
            entry(1, rating = 0, apparent = 12.4),
            entry(2, rating = 1, apparent = 3.6),
            entry(3, rating = -1, apparent = 12.0),   // duplicate stop 12
            entry(4, rating = null, apparent = 30.0)  // unrated → no stop
        )
        assertEquals(listOf(4, 12), runBlocking { recommendations.stops(Sport.CYCLING, true) })
    }

    @Test
    fun `stops follow the apparent-vs-real preference`() {
        dao.ratedEntries = listOf(entry(1, rating = 0, apparent = 5.0, real = 15.0))
        assertEquals(listOf(5), runBlocking { recommendations.stops(Sport.CYCLING, true) })
        assertEquals(listOf(15), runBlocking { recommendations.stops(Sport.CYCLING, false) })
    }

    @Test
    fun `stops is empty without rated history`() {
        assertEquals(emptyList<Int>(), runBlocking { recommendations.stops(Sport.CYCLING, true) })
    }
}
