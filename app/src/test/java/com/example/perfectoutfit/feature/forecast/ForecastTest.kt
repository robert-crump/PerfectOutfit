package com.example.perfectoutfit.feature.forecast

import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.testutil.FakeOpenMeteoApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class ForecastTest {

    private val day = LocalDate.of(2026, 3, 10)
    private val old = day.minusDays(5)

    private fun at(d: LocalDate, h: Int) = d.atTime(h, 0)

    private fun fullDay(d: LocalDate) = (0..23).map { at(d, it) }

    /** Yesterday, today and tomorrow, every hour. */
    private val window = listOf(day.minusDays(1), day, day.plusDays(1)).flatMap(::fullDay)

    private fun forecast(
        api: FakeOpenMeteoApi = FakeOpenMeteoApi(window),
        now: LocalDateTime = day.atTime(14, 35)
    ) = Forecast(api, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC))

    @Test
    fun `location is unknown until refreshed`() = runTest {
        val f = forecast()
        assertNull(f.location)
        assertNull(f.locationFlow.first())
        assertTrue(f.displayWindow().isEmpty())

        f.refresh(48.1, 11.5, "Munich")
        assertEquals(ForecastLocation(48.1, 11.5, "Munich"), f.location)
        assertEquals(window.size, f.hours.first().size)
    }

    @Test
    fun `display window is 24 hours from the current clock hour`() = runTest {
        val f = forecast(now = day.atTime(14, 35))
        f.refresh(1.0, 2.0, "X")

        val shown = f.displayWindow()
        assertEquals(24, shown.size)
        assertEquals(at(day, 14), shown.first().time)
        assertEquals(at(day.plusDays(1), 13), shown.last().time)
    }

    @Test
    fun `display window is shorter when known hours end sooner`() = runTest {
        val f = forecast(now = day.plusDays(1).atTime(20, 0))
        f.refresh(1.0, 2.0, "X")
        assertEquals(4, f.displayWindow().size)
    }

    @Test
    fun `display window is empty when every known hour is in the past`() = runTest {
        val f = forecast(now = day.plusDays(5).atTime(0, 0))
        f.refresh(1.0, 2.0, "X")
        assertTrue(f.displayWindow().isEmpty())
    }

    @Test
    fun `today follows the injected clock`() {
        assertEquals(day, forecast().today())
    }

    @Test
    fun `hoursFor does not fetch when the date is already known`() = runTest {
        val api = FakeOpenMeteoApi(window)
        val f = forecast(api)
        f.refresh(1.0, 2.0, "X")

        assertEquals(24, f.hoursFor(day).size)
        assertTrue(api.dateRangeCalls.isEmpty())
    }

    @Test
    fun `hoursFor fetches a missing date once and merges sorted without duplicates`() = runTest {
        val api = FakeOpenMeteoApi(window, archiveHours = fullDay(old))
        val f = forecast(api)
        f.refresh(1.0, 2.0, "X")
        val known = f.hours.first().size

        assertEquals(24, f.hoursFor(old).size)
        assertEquals(listOf(old.toString() to old.toString()), api.dateRangeCalls)

        val all = f.hours.first()
        assertEquals(known + 24, all.size)
        assertEquals(all.sortedBy { it.time }, all)
        assertEquals(all.map { it.time }.distinct(), all.map { it.time })

        f.hoursFor(old)
        assertEquals(1, api.dateRangeCalls.size)
    }

    @Test
    fun `hoursFor returns empty without fetching while location is unknown`() = runTest {
        val api = FakeOpenMeteoApi(window)
        val f = forecast(api)
        assertTrue(f.hoursFor(day).isEmpty())
        assertTrue(api.dateRangeCalls.isEmpty())
    }

    @Test
    fun `hoursFor propagates a failed fetch and leaves the forecast untouched`() = runTest {
        val api = FakeOpenMeteoApi(window, archiveHours = fullDay(old))
        val f = forecast(api)
        f.refresh(1.0, 2.0, "X")
        val before = f.hours.first()

        api.failNext = true
        try {
            f.hoursFor(old)
            fail("expected exception")
        } catch (e: IOException) {
        }
        assertEquals(before, f.hours.first())
    }

    @Test
    fun `failed refresh keeps the previous forecast`() = runTest {
        val api = FakeOpenMeteoApi(window)
        val f = forecast(api)
        f.refresh(1.0, 2.0, "Old")

        api.failNext = true
        try {
            f.refresh(3.0, 4.0, "New")
            fail("expected exception")
        } catch (e: IOException) {
        }
        assertEquals("Old", f.location?.name)
    }

    // ─── hourNearest ─────────────────────────────────────────────────────────

    private fun hourly(h: Int) = HourlyWeather(
        time = at(day, h), temperatureCelsius = 0.0, apparentTemperatureCelsius = 0.0,
        windSpeedKmh = 0.0, windDirectionDegrees = 0, uvIndex = 0,
        cloudCoverPercent = 0, precipitationProbabilityPercent = 0
    )

    private val sparse = listOf(6, 9, 12).map(::hourly)

    @Test
    fun `hourNearest picks the exact hour`() {
        assertEquals(9, Forecast.hourNearest(sparse, 9)?.time?.hour)
    }

    @Test
    fun `hourNearest falls back to the last hour before`() {
        assertEquals(9, Forecast.hourNearest(sparse, 11)?.time?.hour)
        assertEquals(12, Forecast.hourNearest(sparse, 20)?.time?.hour)
    }

    @Test
    fun `hourNearest falls back to the first hour when all are later`() {
        assertEquals(6, Forecast.hourNearest(sparse, 3)?.time?.hour)
    }

    @Test
    fun `hourNearest is null for no hours`() {
        assertNull(Forecast.hourNearest(emptyList(), 9))
    }
}
