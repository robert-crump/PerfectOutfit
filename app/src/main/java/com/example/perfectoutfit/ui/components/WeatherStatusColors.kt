package com.example.perfectoutfit.ui.components

import androidx.compose.ui.graphics.Color
import com.example.perfectoutfit.ui.theme.StatusColors

/**
 * Shared severity-color mapping for weather indicators (UV index, wind speed,
 * rain probability), used by both [WeatherCard] and the home screen's compact
 * weather row so the thresholds and colors live in one place.
 */
fun uvStatusColor(uvIndex: Int, colors: StatusColors): Color = when {
    uvIndex <= 2 -> colors.good
    uvIndex <= 5 -> colors.moderate
    else -> colors.severe
}

fun windStatusColor(speedKmh: Double, colors: StatusColors): Color = when {
    speedKmh < 10 -> colors.good
    speedKmh <= 20 -> colors.moderate
    else -> colors.severe
}

fun rainStatusColor(probabilityPercent: Int, colors: StatusColors): Color = when {
    probabilityPercent < 20 -> colors.good
    probabilityPercent < 50 -> colors.moderate
    else -> colors.severe
}
