package com.example.perfectoutfit.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToInt

/*
 * How weather values are rounded and written (issue #53; see "Weather formatting" in CONTEXT.md).
 * Stored and fetched values stay raw; rounding happens here, at the edge. Where a rule judges a
 * rounded quantity (rain likely, rain class), it judges the rounded value, so a screen never
 * shows one thing while the app acts on another.
 */

/** A percentage rounded to the nearest ten, half up: 44 → 40, 45 → 50, 95 → 100. */
fun roundedPercent(percent: Int): Int = (percent + 5) / 10 * 10

/** An amount rounded to the nearest 0.1 mm, half up: 0.05 → 0.1, 4.04 → 4.0. */
fun roundedMm(mm: Double): Double = BigDecimal.valueOf(mm).setScale(1, RoundingMode.HALF_UP).toDouble()

/** Precipitation probability (rounded) from which rain counts as likely. */
private const val RAIN_LIKELY_PERCENT = 50

/** Rain counts as likely when the probability, as displayed, is at least 50%. */
val WeatherReading.isRainLikely: Boolean
    get() = roundedPercent(precipitationProbabilityPercent) >= RAIN_LIKELY_PERCENT

/** "40%": rounded to tens, no space before the sign. */
fun formatPercent(percent: Int): String = "${roundedPercent(percent)}%"

/** "1.3 mm", "0.0 mm", or "— mm" when no amount is known; always one decimal and a ".". */
fun formatMm(mm: Double?): String =
    if (mm == null) "— mm" else String.format(Locale.ROOT, "%.1f mm", roundedMm(mm))

/**
 * "1.3 mm · light rain", or "— mm" when no amount is known. The caller picks the class: the
 * expected one for an hour being dressed for, the logged one for an hour being logged.
 */
fun rainAmountLine(mm: Double?, rainClass: RainClass): String =
    if (mm == null) formatMm(null) else "${formatMm(mm)} · ${rainClass.label}"

/** "12 km/h", rounded to the nearest whole km/h. */
fun formatWindKmh(kmh: Double): String = "${kmh.roundToInt()} km/h"
