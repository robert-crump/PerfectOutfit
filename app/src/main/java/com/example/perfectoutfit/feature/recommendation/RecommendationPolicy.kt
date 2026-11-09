package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.roundedReferenceTemp
import kotlin.math.abs

/**
 * Matching rules for "Recommendation" in CONTEXT.md: only rated entries within ±[WINDOW] °C
 * count; surviving perfect entries beat everything else, then entries pointing toward the
 * target, then entries known to be wrong for it.
 */
internal object RecommendationPolicy {

    private const val WINDOW = 2

    fun findRecommendation(
        candidates: List<OutfitEntryWithDetails>,
        targetTemp: Int,
        useApparent: Boolean
    ): OutfitEntryWithDetails? {
        val window = Window(candidates, targetTemp, useApparent)
        val perfect = window.survivingPerfect()
        if (perfect.isNotEmpty()) {
            val counts = perfect.groupingBy { combinationOf(it) }.eachCount()
            return perfect.minWithOrNull(
                compareBy<OutfitEntryWithDetails> { window.distance(it) }
                    .thenByDescending { combinationOf(it)?.let(counts::getValue) ?: 1 }
                    .thenByDescending { it.weatherSnapshot.timestamp }
            )
        }
        // Cancelled perfect entries stay out; their combination's negative entries may still win.
        return window.entries.filter { it.entry.rating != ComfortRating.PERFECT }.minWithOrNull(
            compareBy<OutfitEntryWithDetails> { window.isKnownWrong(it) }
                .thenBy { window.distance(it) }
                .thenBy { it.entry.rating!!.matchPriority }
                .thenByDescending { it.weatherSnapshot.timestamp }
        )
    }

    fun likelyItemIds(
        candidates: List<OutfitEntryWithDetails>,
        targetTemp: Int,
        useApparent: Boolean
    ): Set<Long> = Window(candidates, targetTemp, useApparent).survivingPerfect()
        .flatMap { it.clothingItems }
        .map { it.id }
        .toSet()

    /** An entry's combination (its exact item set), or null for an entry without items. */
    private fun combinationOf(entry: OutfitEntryWithDetails): Set<Long>? =
        entry.clothingItems.map { it.id }.toSet().takeIf { it.isNotEmpty() }

    private class Window(
        candidates: List<OutfitEntryWithDetails>,
        private val target: Int,
        private val useApparent: Boolean
    ) {
        val entries: List<OutfitEntryWithDetails> = candidates.filter {
            it.entry.rating != null && distance(it) <= WINDOW
        }

        fun temp(entry: OutfitEntryWithDetails): Int = entry.weatherSnapshot.roundedReferenceTemp(useApparent)

        fun distance(entry: OutfitEntryWithDetails): Int = abs(temp(entry) - target)

        /** Too cold at or above the target, or too hot at or below it. */
        fun isKnownWrong(entry: OutfitEntryWithDetails): Boolean = when (entry.entry.rating) {
            ComfortRating.TOO_COLD -> temp(entry) >= target
            ComfortRating.TOO_HOT -> temp(entry) <= target
            else -> false
        }

        /** Perfect entries whose combination has no newer known-wrong entry in the window. */
        fun survivingPerfect(): List<OutfitEntryWithDetails> {
            val newestWrong: Map<Set<Long>, Long> = entries
                .filter { isKnownWrong(it) }
                .mapNotNull { e -> combinationOf(e)?.let { it to e.weatherSnapshot.timestamp } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, times) -> times.max() }
            return entries.filter { e ->
                e.entry.rating == ComfortRating.PERFECT &&
                    combinationOf(e)?.let { newestWrong[it] }?.let { it <= e.weatherSnapshot.timestamp } ?: true
            }
        }
    }
}
