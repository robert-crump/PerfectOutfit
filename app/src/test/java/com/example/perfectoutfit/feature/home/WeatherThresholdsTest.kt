package com.example.perfectoutfit.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherThresholdsTest {

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
}
