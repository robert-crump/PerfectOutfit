package com.example.perfectoutfit.core.model

import kotlin.math.roundToInt

/**
 * The seven measurements shared by a forecast hour and a persisted [WeatherSnapshot].
 * Exists so both are judged by the one reference-temperature rule.
 */
interface WeatherReading {
    val temperatureCelsius: Double
    val apparentTemperatureCelsius: Double
    val windSpeedKmh: Double
    val windDirectionDegrees: Int
    val uvIndex: Int
    val cloudCoverPercent: Int
    val precipitationProbabilityPercent: Int
}

/**
 * Reference temperature: the single temperature this app reasons about for a reading,
 * selected by the user's apparent-vs-real preference. Callers round at the edge.
 */
fun WeatherReading.referenceTemp(useApparent: Boolean): Double =
    if (useApparent) apparentTemperatureCelsius else temperatureCelsius

/** Whole degrees as the app compares and displays them; the one place a temperature is rounded. */
fun roundedTemp(celsius: Double): Int = celsius.roundToInt()

/** [referenceTemp] rounded to whole degrees. */
fun WeatherReading.roundedReferenceTemp(useApparent: Boolean): Int = roundedTemp(referenceTemp(useApparent))
