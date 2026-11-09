package com.example.perfectoutfit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** App colours the Material scheme has no slot for; read through [MaterialTheme.extendedColors]. */
@Immutable
data class ExtendedColors(
    val coldContainer: Color,
    val onColdContainer: Color,
    val hotContainer: Color,
    val onHotContainer: Color
)

internal val LightExtendedColors = ExtendedColors(
    coldContainer = custom_light_coldContainer,
    onColdContainer = custom_light_onColdContainer,
    hotContainer = custom_light_hotContainer,
    onHotContainer = custom_light_onHotContainer
)

internal val DarkExtendedColors = ExtendedColors(
    coldContainer = custom_dark_coldContainer,
    onColdContainer = custom_dark_onColdContainer,
    hotContainer = custom_dark_hotContainer,
    onHotContainer = custom_dark_onHotContainer
)

internal val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.extendedColors: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
