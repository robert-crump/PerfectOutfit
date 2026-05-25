package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import kotlin.math.roundToInt

object RecommendationPolicy {

    fun findRecommendation(
        candidates: List<OutfitEntryWithDetails>,
        targetTemp: Int,
        useApparent: Boolean
    ): OutfitEntryWithDetails? {
        val rated = candidates.filter { it.entry.comfortRating != null }

        // Step 1: exact match → newest entry wins regardless of rating
        val exact = rated
            .filter { it.roundedTemp(useApparent) == targetTemp }
            .maxByOrNull { it.weatherSnapshot.timestamp }
        if (exact != null) return exact

        // Step 2: ±1°C → best rating (0 > 1 > -1), then newest
        val plusMinus1 = rated
            .filter { it.roundedTemp(useApparent) in (targetTemp - 1)..(targetTemp + 1) }
            .minWithOrNull(byRatingThenNewest)
        if (plusMinus1 != null) return plusMinus1

        // Step 3: ±2°C → best rating (0 > 1 > -1), then newest
        return rated
            .filter { it.roundedTemp(useApparent) in (targetTemp - 2)..(targetTemp + 2) }
            .minWithOrNull(byRatingThenNewest)
    }

    fun likelyItemIds(
        candidates: List<OutfitEntryWithDetails>,
        targetTemp: Int,
        useApparent: Boolean
    ): Set<Long> = candidates
        .filter { it.entry.comfortRating == 0 && it.roundedTemp(useApparent) in (targetTemp - 2)..(targetTemp + 2) }
        .flatMap { it.clothingItems }
        .map { it.id }
        .toSet()

    // Comparator: rating priority (0 → 1 → -1), tiebreak by newest timestamp
    private val byRatingThenNewest: Comparator<OutfitEntryWithDetails> =
        compareBy<OutfitEntryWithDetails> { ratingPriority(it.entry.comfortRating!!) }
            .thenByDescending { it.weatherSnapshot.timestamp }

    private fun ratingPriority(rating: Int): Int = when (rating) {
        0 -> 0
        1 -> 1
        else -> 2
    }
}

internal fun OutfitEntryWithDetails.roundedTemp(useApparent: Boolean): Int =
    (if (useApparent) weatherSnapshot.apparentTemperatureCelsius
    else weatherSnapshot.temperatureCelsius).roundToInt()
