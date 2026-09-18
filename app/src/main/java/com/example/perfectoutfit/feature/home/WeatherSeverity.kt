package com.example.perfectoutfit.feature.home

/**
 * How concerning a single weather measurement (UV index, wind speed, or rain probability)
 * is for an hour. [HIGH] is the level at which the Home screen shows a warning card.
 */
enum class WeatherSeverity { NONE, NOTABLE, HIGH }

/**
 * The single source of truth for the UV, wind, and rain severity bands. Both the Home
 * screen's warning cards (shown at [WeatherSeverity.HIGH]) and
 * [com.example.perfectoutfit.ui.components.WeatherStatusColors] (which colours by band)
 * consult this object, so severity and colour can never disagree.
 *
 * UV HIGH is >= 5 (not the WHO "high" band of >= 6) because that is the threshold users
 * already see on the Home screen; see CONTEXT.md for the full rationale.
 */
object WeatherThresholds {
    fun uvSeverity(uvIndex: Int): WeatherSeverity = when {
        uvIndex <= 2 -> WeatherSeverity.NONE
        uvIndex <= 4 -> WeatherSeverity.NOTABLE
        else -> WeatherSeverity.HIGH
    }

    fun windSeverity(speedKmh: Double): WeatherSeverity = when {
        speedKmh < 10.0 -> WeatherSeverity.NONE
        speedKmh < 20.0 -> WeatherSeverity.NOTABLE
        else -> WeatherSeverity.HIGH
    }

    fun rainSeverity(probabilityPercent: Int): WeatherSeverity = when {
        probabilityPercent < 20 -> WeatherSeverity.NONE
        probabilityPercent < 50 -> WeatherSeverity.NOTABLE
        else -> WeatherSeverity.HIGH
    }
}
