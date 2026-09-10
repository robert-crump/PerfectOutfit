package com.example.perfectoutfit.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Three-level severity scale (good / moderate / severe) used for weather
 * indicators such as UV index, wind speed, and rain probability.
 *
 * MD3's baseline color roles don't include a severity scale, so this follows
 * Material's guidance for extending a scheme with custom colors
 * (https://m3.material.io/styles/color/advanced/define-new-colors): a fixed
 * light/dark pair kept alongside the seeded scheme, the same way `error` is
 * static rather than generated from dynamic color.
 */
data class StatusColors(
    val good: Color,
    val moderate: Color,
    val severe: Color
)

private val LightStatusColors = StatusColors(
    good = Color(0xFF2E7D32),
    moderate = Color(0xFFF57F17),
    severe = Color(0xFFC62828)
)

private val DarkStatusColors = StatusColors(
    good = Color(0xFF81C784),
    moderate = Color(0xFFFFD54F),
    severe = Color(0xFFE57373)
)

val LocalStatusColors: ProvidableCompositionLocal<StatusColors> =
    staticCompositionLocalOf { LightStatusColors }

@Composable
internal fun statusColorsFor(darkTheme: Boolean): StatusColors =
    if (darkTheme) DarkStatusColors else LightStatusColors
