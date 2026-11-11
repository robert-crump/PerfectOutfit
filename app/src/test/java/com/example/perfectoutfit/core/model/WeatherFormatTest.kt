package com.example.perfectoutfit.core.model

import com.example.perfectoutfit.feature.home.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale

class WeatherFormatTest {

    private fun hour(percent: Int) = HourlyWeather(
        time = LocalDateTime.of(2026, 1, 1, 12, 0),
        temperatureCelsius = 10.0,
        apparentTemperatureCelsius = 10.0,
        windSpeedKmh = 0.0,
        windDirectionDegrees = 0,
        uvIndex = 0,
        cloudCoverPercent = 0,
        precipitationProbabilityPercent = percent
    )

    @Test
    fun `percent rounds to the nearest ten, half up`() {
        assertEquals(0, roundedPercent(0))
        assertEquals(0, roundedPercent(4))
        assertEquals(10, roundedPercent(5))
        assertEquals(40, roundedPercent(44))
        assertEquals(50, roundedPercent(45))
        assertEquals(90, roundedPercent(94))
        assertEquals(100, roundedPercent(95))
        assertEquals(100, roundedPercent(100))
    }

    @Test
    fun `rain is likely from a displayed 50 percent`() {
        assertFalse(hour(44).isRainLikely)
        assertTrue(hour(45).isRainLikely)
        assertTrue(hour(50).isRainLikely)
    }

    @Test
    fun `percent is written without a space`() {
        assertEquals("40%", formatPercent(37))
        assertEquals("50%", formatPercent(45))
    }

    @Test
    fun `amount rounds to a tenth, half up`() {
        assertEquals(0.0, roundedMm(0.04), 0.0)
        assertEquals(0.1, roundedMm(0.05), 0.0)
        assertEquals(0.1, roundedMm(0.08), 0.0)
        assertEquals(0.5, roundedMm(0.45), 0.0)
        assertEquals(4.0, roundedMm(4.04), 0.0)
        assertEquals(4.1, roundedMm(4.05), 0.0)
    }

    @Test
    fun `amount always has one decimal`() {
        assertEquals("0.0 mm", formatMm(0.0))
        assertEquals("1.3 mm", formatMm(1.3))
        assertEquals("12.0 mm", formatMm(12.0))
        assertEquals("0.1 mm", formatMm(0.08))
    }

    @Test
    fun `a missing amount is a dash`() {
        assertEquals("— mm", formatMm(null))
        assertEquals("— mm", rainAmountLine(null, RainClass.LIGHT))
    }

    @Test
    fun `amount uses a dot whatever the default locale`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.3 mm", formatMm(1.3))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `rain amount line names the given class`() {
        assertEquals("1.3 mm · moderate rain", rainAmountLine(1.3, RainClass.MODERATE))
        assertEquals("0.0 mm · light rain", rainAmountLine(0.0, RainClass.LIGHT))
    }

    @Test
    fun `wind rounds instead of truncating`() {
        assertEquals("12 km/h", formatWindKmh(12.4))
        assertEquals("13 km/h", formatWindKmh(12.5))
        assertEquals("13 km/h", formatWindKmh(12.9))
    }
}
