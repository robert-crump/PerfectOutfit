package com.example.perfectoutfit.feature.home

import java.time.LocalDateTime

/**
 * Rain is likely from [from] on ([startsNow] when that is the current hour), peaking at
 * [peakPercent] for the rest of that day.
 */
data class RainAhead(val from: LocalDateTime, val startsNow: Boolean, val peakPercent: Int)

/**
 * When the Home screen's result step shows its UV warning and rain-ahead cards.
 *
 * UV is high at >= 5 (not the WHO "high" band of >= 6) because that is the threshold users
 * already saw on the Home screen (issue #1); see CONTEXT.md.
 */
object WeatherThresholds {
    const val RAIN_LIKELY_PERCENT = 50

    fun isUvHigh(uvIndex: Int): Boolean = uvIndex >= 5

    /**
     * The first hour, from the first of [hours] (the current hour) to the end of its day, whose
     * precipitation probability is at least [RAIN_LIKELY_PERCENT], with the day's remaining
     * peak; null if none.
     */
    fun rainAhead(hours: List<HourlyWeather>): RainAhead? {
        val now = hours.firstOrNull() ?: return null
        val restOfDay = hours.takeWhile { it.time.toLocalDate() == now.time.toLocalDate() }
        val first = restOfDay.firstOrNull { it.precipitationProbabilityPercent >= RAIN_LIKELY_PERCENT }
            ?: return null
        return RainAhead(
            from = first.time,
            startsNow = first === now,
            peakPercent = restOfDay.maxOf { it.precipitationProbabilityPercent }
        )
    }
}
