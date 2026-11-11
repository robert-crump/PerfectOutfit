package com.example.perfectoutfit.core.model

import com.example.perfectoutfit.feature.home.HourlyWeather
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class RainClassTest {

    private fun hour(mm: Double?, percent: Int = 0) = HourlyWeather(
        time = LocalDateTime.of(2026, 1, 1, 12, 0),
        temperatureCelsius = 10.0,
        apparentTemperatureCelsius = 10.0,
        windSpeedKmh = 0.0,
        windDirectionDegrees = 0,
        uvIndex = 0,
        cloudCoverPercent = 0,
        precipitationProbabilityPercent = percent,
        precipitationMm = mm
    )

    @Test
    fun `amount boundaries`() {
        assertEquals(RainClass.DRY, RainClass.ofAmount(0.0))
        assertEquals(RainClass.DRY, RainClass.ofAmount(0.04))
        assertEquals(RainClass.LIGHT, RainClass.ofAmount(0.1))
        assertEquals(RainClass.LIGHT, RainClass.ofAmount(0.4))
        assertEquals(RainClass.MODERATE, RainClass.ofAmount(0.5))
        assertEquals(RainClass.MODERATE, RainClass.ofAmount(4.0))
        assertEquals(RainClass.HEAVY, RainClass.ofAmount(4.1))
    }

    @Test
    fun `amounts are classed as displayed, rounded to a tenth`() {
        assertEquals(RainClass.LIGHT, RainClass.ofAmount(0.08)) // shown as 0.1 mm
        assertEquals(RainClass.MODERATE, RainClass.ofAmount(0.46)) // shown as 0.5 mm
        assertEquals(RainClass.MODERATE, RainClass.ofAmount(4.04)) // shown as 4.0 mm
    }

    @Test
    fun `distance counts steps symmetrically`() {
        assertEquals(3, RainClass.DRY.distanceTo(RainClass.HEAVY))
        assertEquals(3, RainClass.HEAVY.distanceTo(RainClass.DRY))
        assertEquals(0, RainClass.LIGHT.distanceTo(RainClass.LIGHT))
    }

    @Test
    fun `a logged hour goes by its amount, ignoring probability`() {
        assertEquals(RainClass.DRY, hour(mm = 0.0, percent = 80).loggedRainClass())
        assertEquals(RainClass.MODERATE, hour(mm = 2.0, percent = 10).loggedRainClass())
    }

    @Test
    fun `a logged hour without an amount is light when rain was likely, else dry`() {
        assertEquals(RainClass.LIGHT, hour(mm = null, percent = 45).loggedRainClass())
        assertEquals(RainClass.DRY, hour(mm = null, percent = 44).loggedRainClass())
    }

    @Test
    fun `an expected hour is raised to light by a likely probability, never further`() {
        assertEquals(RainClass.LIGHT, hour(mm = 0.0, percent = 60).expectedRainClass())
        assertEquals(RainClass.DRY, hour(mm = 0.0, percent = 44).expectedRainClass())
        assertEquals(RainClass.LIGHT, hour(mm = 0.0, percent = 45).expectedRainClass())
        assertEquals(RainClass.MODERATE, hour(mm = 1.0, percent = 90).expectedRainClass())
        assertEquals(RainClass.HEAVY, hour(mm = 6.0, percent = 10).expectedRainClass())
        assertEquals(RainClass.LIGHT, hour(mm = null, percent = 60).expectedRainClass())
    }
}
