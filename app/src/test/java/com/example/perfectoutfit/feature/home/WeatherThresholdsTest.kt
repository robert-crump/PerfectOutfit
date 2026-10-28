package com.example.perfectoutfit.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class WeatherThresholdsTest {

    private val start = LocalDateTime.of(2026, 10, 28, 20, 0)

    /** Consecutive hours from 20:00, one per rain probability. */
    private fun hours(vararg rain: Int) = rain.mapIndexed { i, percent ->
        HourlyWeather(
            time = start.plusHours(i.toLong()),
            temperatureCelsius = 10.0,
            apparentTemperatureCelsius = 10.0,
            windSpeedKmh = 0.0,
            windDirectionDegrees = 0,
            uvIndex = 0,
            cloudCoverPercent = 0,
            precipitationProbabilityPercent = percent
        )
    }

    @Test
    fun `UV is not high below 5`() {
        assertFalse(WeatherThresholds.isUvHigh(0))
        assertFalse(WeatherThresholds.isUvHigh(4))
    }

    @Test
    fun `UV is high at and above 5`() {
        assertTrue(WeatherThresholds.isUvHigh(5))
        assertTrue(WeatherThresholds.isUvHigh(11))
    }

    @Test
    fun `no rain ahead when every hour today stays below 50 percent`() {
        assertNull(WeatherThresholds.rainAhead(hours(0, 20, 49, 10)))
    }

    @Test
    fun `no rain ahead without hours`() {
        assertNull(WeatherThresholds.rainAhead(emptyList()))
    }

    @Test
    fun `rain ahead starts at the first likely hour and reports the day's peak`() {
        val rain = WeatherThresholds.rainAhead(hours(10, 50, 80, 30))
        assertEquals(RainAhead(from = start.plusHours(1), startsNow = false, peakPercent = 80), rain)
    }

    @Test
    fun `rain likely in the current hour starts now`() {
        val rain = WeatherThresholds.rainAhead(hours(60, 0, 90))
        assertEquals(RainAhead(from = start, startsNow = true, peakPercent = 90), rain)
    }

    @Test
    fun `rain after midnight is not rain ahead today`() {
        // 20:00 to 23:00 dry, 00:00 wet.
        assertNull(WeatherThresholds.rainAhead(hours(0, 0, 0, 0, 90)))
    }

    @Test
    fun `the peak ignores hours after midnight`() {
        assertEquals(60, WeatherThresholds.rainAhead(hours(0, 60, 0, 0, 90))?.peakPercent)
    }
}
