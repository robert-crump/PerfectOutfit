package com.example.perfectoutfit.feature.home

/**
 * When the Home screen's result step shows its UV warning card.
 *
 * UV is high at >= 5 (not the WHO "high" band of >= 6) because that is the threshold users
 * already saw on the Home screen (issue #1); see CONTEXT.md.
 */
object WeatherThresholds {
    fun isUvHigh(uvIndex: Int): Boolean = uvIndex >= 5
}
