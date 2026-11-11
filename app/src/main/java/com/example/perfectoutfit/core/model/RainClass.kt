package com.example.perfectoutfit.core.model

import kotlin.math.abs

/**
 * How wet an hour is, from its precipitation amount (see "Rain class" in CONTEXT.md).
 * Declaration order is wetness order, so [distanceTo] counts steps between classes.
 */
enum class RainClass(
    /** As the recommendation card names it: "Worn at 9°C in light rain". */
    val label: String
) {
    DRY("dry"),
    LIGHT("light rain"),
    MODERATE("moderate rain"),
    HEAVY("heavy rain");

    val isWet: Boolean get() = this != DRY

    /** Symmetric number of steps between two classes (dry ↔ heavy is 3). */
    fun distanceTo(other: RainClass): Int = abs(ordinal - other.ordinal)

    companion object {
        /**
         * Dry < 0.1 mm, light < 0.5 mm, moderate up to 4 mm, heavy above 4 mm (per hour), judged
         * on the amount as displayed ([roundedMm]), so "0.1 mm" is never dry.
         */
        fun ofAmount(mm: Double): RainClass {
            val shown = roundedMm(mm)
            return when {
                shown < 0.1 -> DRY
                shown < 0.5 -> LIGHT
                shown <= 4.0 -> MODERATE
                else -> HEAVY
            }
        }
    }
}

/**
 * The class of an hour as worn (a logged snapshot, or the hour being logged): its amount; only
 * when no amount is known (snapshots from before amounts were stored) a likely probability
 * ([isRainLikely]) counts as [RainClass.LIGHT].
 */
fun WeatherReading.loggedRainClass(): RainClass = precipitationMm?.let(RainClass::ofAmount)
    ?: if (isRainLikely) RainClass.LIGHT else RainClass.DRY

/**
 * The class of an hour being dressed for: its forecast amount, raised to at least
 * [RainClass.LIGHT] by a likely probability ([isRainLikely]). Probability never raises it further.
 */
fun WeatherReading.expectedRainClass(): RainClass {
    val byAmount = precipitationMm?.let(RainClass::ofAmount) ?: RainClass.DRY
    return if (isRainLikely && byAmount == RainClass.DRY) RainClass.LIGHT else byAmount
}
