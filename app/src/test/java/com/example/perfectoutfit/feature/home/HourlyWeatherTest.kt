package com.example.perfectoutfit.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class HourlyWeatherTest {

    private fun hour(real: Double, apparent: Double) = HourlyWeather(
        time = LocalDateTime.of(2026, 1, 1, 12, 0),
        temperatureCelsius = real,
        apparentTemperatureCelsius = apparent,
        windSpeedKmh = 0.0,
        windDirectionDegrees = 0,
        uvIndex = 0,
        cloudCoverPercent = 0,
        precipitationProbabilityPercent = 0
    )

    @Test
    fun `referenceTemp returns apparent temperature when useApparent is true`() {
        assertEquals(3.5, hour(real = 8.0, apparent = 3.5).referenceTemp(useApparent = true), 0.0)
    }

    @Test
    fun `referenceTemp returns real temperature when useApparent is false`() {
        assertEquals(8.0, hour(real = 8.0, apparent = 3.5).referenceTemp(useApparent = false), 0.0)
    }

    @Test
    fun `referenceTemp handles negative temperatures per basis`() {
        val h = hour(real = -2.0, apparent = -9.0)
        assertEquals(-9.0, h.referenceTemp(useApparent = true), 0.0)
        assertEquals(-2.0, h.referenceTemp(useApparent = false), 0.0)
    }
}
