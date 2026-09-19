package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.core.model.WeatherReading
import com.example.perfectoutfit.core.model.referenceTemp
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Recommendations for the coldest and warmest hours of a workout window. */
data class WorkoutWindowRecommendation(
    val coldestHourIndex: Int,
    val warmestHourIndex: Int,
    val coldest: OutfitEntryWithDetails?,
    val warmest: OutfitEntryWithDetails?
)

/**
 * The one entry point for outfit recommendations (see "Recommendation" in CONTEXT.md).
 * Owns loading rated history, rounding the reference temperature, the tiered matching,
 * likely-item lookup, the Explorer stop list and the workout-window extremes.
 *
 * Callers pass the apparent-vs-real preference in and observe it themselves.
 */
@Singleton
class Recommendations @Inject constructor(
    private val outfitEntryDao: OutfitEntryDao
) {
    /** Best rated outfit for [referenceTemp] (rounded here), or null. */
    suspend fun find(sport: Sport, referenceTemp: Double, useApparent: Boolean): OutfitEntryWithDetails? =
        RecommendationPolicy.findRecommendation(
            outfitEntryDao.getRatedEntriesWithDetails(sport),
            referenceTemp.roundToInt(),
            useApparent
        )

    /**
     * Coldest/warmest recommendations over the first [durationHours] of [hours]; a
     * one-hour window has coldest == warmest. Returns null when [hours] is empty, or when
     * [isCurrent] turned false while the queries ran (the caller's sport or duration
     * changed, so the result is stale and must be discarded).
     */
    suspend fun findForWorkoutWindow(
        sport: Sport,
        hours: List<WeatherReading>,
        durationHours: Int,
        useApparent: Boolean,
        isCurrent: () -> Boolean = { true }
    ): WorkoutWindowRecommendation? {
        if (hours.isEmpty()) return null
        val (coldestIdx, warmestIdx) = workoutExtremes(hours, durationHours, useApparent)
        val coldest = find(sport, hours[coldestIdx].referenceTemp(useApparent), useApparent)
        val warmest = find(sport, hours[warmestIdx].referenceTemp(useApparent), useApparent)
        if (!isCurrent()) return null
        return WorkoutWindowRecommendation(coldestIdx, warmestIdx, coldest, warmest)
    }

    /** Ids of clothing items from perfect-rated outfits near [referenceTemp]. */
    suspend fun likelyItemIds(sport: Sport, referenceTemp: Double, useApparent: Boolean): Set<Long> =
        RecommendationPolicy.likelyItemIds(
            outfitEntryDao.getRatedEntriesWithDetails(sport),
            referenceTemp.roundToInt(),
            useApparent
        )

    /** Sorted, distinct rounded reference temperatures that have rated history for [sport]. */
    suspend fun stops(sport: Sport, useApparent: Boolean): List<Int> =
        RecommendationPolicy.stops(outfitEntryDao.getRatedEntriesWithDetails(sport), useApparent)

    companion object {
        /**
         * Indices of the coldest and warmest hours within the first [durationHours] of
         * [hours]. Ties go to the later hour for both.
         */
        fun workoutExtremes(
            hours: List<WeatherReading>,
            durationHours: Int,
            useApparent: Boolean
        ): Pair<Int, Int> {
            val window = hours.take(durationHours)
            var coldestIdx = 0
            var warmestIdx = 0
            window.forEachIndexed { i, h ->
                val temp = h.referenceTemp(useApparent)
                if (temp <= window[coldestIdx].referenceTemp(useApparent)) coldestIdx = i
                if (temp >= window[warmestIdx].referenceTemp(useApparent)) warmestIdx = i
            }
            return coldestIdx to warmestIdx
        }
    }
}
