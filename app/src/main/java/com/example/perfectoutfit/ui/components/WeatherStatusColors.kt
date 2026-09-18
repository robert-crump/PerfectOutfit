package com.example.perfectoutfit.ui.components

import androidx.compose.ui.graphics.Color
import com.example.perfectoutfit.feature.home.WeatherSeverity
import com.example.perfectoutfit.feature.home.WeatherThresholds
import com.example.perfectoutfit.ui.theme.StatusColors

/**
 * Colours a weather indicator (UV index, wind speed, rain probability) by the severity
 * band from [WeatherThresholds], the single place those bands are defined.
 */
private fun StatusColors.forSeverity(severity: WeatherSeverity): Color = when (severity) {
    WeatherSeverity.NONE -> good
    WeatherSeverity.NOTABLE -> moderate
    WeatherSeverity.HIGH -> severe
}

fun uvStatusColor(uvIndex: Int, colors: StatusColors): Color =
    colors.forSeverity(WeatherThresholds.uvSeverity(uvIndex))

fun windStatusColor(speedKmh: Double, colors: StatusColors): Color =
    colors.forSeverity(WeatherThresholds.windSeverity(speedKmh))

fun rainStatusColor(probabilityPercent: Int, colors: StatusColors): Color =
    colors.forSeverity(WeatherThresholds.rainSeverity(probabilityPercent))
