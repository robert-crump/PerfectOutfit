package com.example.perfectoutfit.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherThresholdsTest {

    @Test
    fun `uvSeverity is NONE at and below 2`() {
        assertEquals(WeatherSeverity.NONE, WeatherThresholds.uvSeverity(0))
        assertEquals(WeatherSeverity.NONE, WeatherThresholds.uvSeverity(2))
    }

    @Test
    fun `uvSeverity is NOTABLE between 3 and 4`() {
        assertEquals(WeatherSeverity.NOTABLE, WeatherThresholds.uvSeverity(3))
        assertEquals(WeatherSeverity.NOTABLE, WeatherThresholds.uvSeverity(4))
    }

    @Test
    fun `uvSeverity is HIGH at and above 5`() {
        assertEquals(WeatherSeverity.HIGH, WeatherThresholds.uvSeverity(5))
        assertEquals(WeatherSeverity.HIGH, WeatherThresholds.uvSeverity(11))
    }

    @Test
    fun `windSeverity is NONE below 10`() {
        assertEquals(WeatherSeverity.NONE, WeatherThresholds.windSeverity(0.0))
        assertEquals(WeatherSeverity.NONE, WeatherThresholds.windSeverity(9.99))
    }

    @Test
    fun `windSeverity is NOTABLE between 10 and just under 20`() {
        assertEquals(WeatherSeverity.NOTABLE, WeatherThresholds.windSeverity(10.0))
        assertEquals(WeatherSeverity.NOTABLE, WeatherThresholds.windSeverity(19.99))
    }

    @Test
    fun `windSeverity is HIGH at and above 20`() {
        assertEquals(WeatherSeverity.HIGH, WeatherThresholds.windSeverity(20.0))
        assertEquals(WeatherSeverity.HIGH, WeatherThresholds.windSeverity(45.0))
    }

    @Test
    fun `rainSeverity is NONE below 20 percent`() {
        assertEquals(WeatherSeverity.NONE, WeatherThresholds.rainSeverity(0))
        assertEquals(WeatherSeverity.NONE, WeatherThresholds.rainSeverity(19))
    }

    @Test
    fun `rainSeverity is NOTABLE between 20 and 49 percent`() {
        assertEquals(WeatherSeverity.NOTABLE, WeatherThresholds.rainSeverity(20))
        assertEquals(WeatherSeverity.NOTABLE, WeatherThresholds.rainSeverity(49))
    }

    @Test
    fun `rainSeverity is HIGH at and above 50 percent`() {
        assertEquals(WeatherSeverity.HIGH, WeatherThresholds.rainSeverity(50))
        assertEquals(WeatherSeverity.HIGH, WeatherThresholds.rainSeverity(100))
    }
}
