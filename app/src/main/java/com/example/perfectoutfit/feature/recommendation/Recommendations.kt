package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.database.dao.ClothingItemDao
import com.example.perfectoutfit.core.model.roundedTemp
import com.example.perfectoutfit.core.database.dao.OutfitEntryDao
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.WeatherReading
import com.example.perfectoutfit.core.model.referenceTemp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
 * Owns loading rated history, rounding the reference temperature, the matching,
 * likely-item lookup, Explorer's temperature chart and the workout-window extremes.
 *
 * Callers pass the apparent-vs-real preference in and observe it themselves.
 */
@Singleton
class Recommendations @Inject constructor(
    private val outfitEntryDao: OutfitEntryDao,
    private val clothingItemDao: ClothingItemDao
) {
    /** Best rated outfit for [referenceTemp] (rounded here), or null. */
    suspend fun find(activityType: ActivityType, referenceTemp: Double, useApparent: Boolean): OutfitEntryWithDetails? =
        RecommendationPolicy.findRecommendation(
            outfitEntryDao.getRatedEntriesWithDetails(activityType),
            roundedTemp(referenceTemp),
            useApparent
        )

    /**
     * Coldest/warmest recommendations over the first [durationHours] of [hours]; a
     * one-hour window has coldest == warmest. Returns null when [hours] is empty, or when
     * [isCurrent] turned false while the queries ran (the caller's activityType or duration
     * changed, so the result is stale and must be discarded).
     */
    suspend fun findForWorkoutWindow(
        activityType: ActivityType,
        hours: List<WeatherReading>,
        durationHours: Int,
        useApparent: Boolean,
        isCurrent: () -> Boolean = { true }
    ): WorkoutWindowRecommendation? {
        if (hours.isEmpty()) return null
        val (coldestIdx, warmestIdx) = workoutExtremes(hours, durationHours, useApparent)
        val coldest = find(activityType, hours[coldestIdx].referenceTemp(useApparent), useApparent)
        val warmest = find(activityType, hours[warmestIdx].referenceTemp(useApparent), useApparent)
        if (!isCurrent()) return null
        return WorkoutWindowRecommendation(coldestIdx, warmestIdx, coldest, warmest)
    }

    /** Ids of clothing items from surviving perfect-rated outfits near [referenceTemp]. */
    suspend fun likelyItemIds(activityType: ActivityType, referenceTemp: Double, useApparent: Boolean): Set<Long> =
        RecommendationPolicy.likelyItemIds(
            outfitEntryDao.getRatedEntriesWithDetails(activityType),
            roundedTemp(referenceTemp),
            useApparent
        )

    /** Feels-like vs actual over all rated history, re-emitted whenever entries change. */
    fun modeComparison(): Flow<ModeComparison> =
        outfitEntryDao.getAllWithDetails().map(ModeComparisonPolicy::compare)

    /**
     * Explorer's temperature chart for [activityType], re-emitted whenever its entries or the
     * items tagged for it change.
     */
    fun temperatureChart(activityType: ActivityType, useApparent: Boolean): Flow<TemperatureChart> =
        combine(
            outfitEntryDao.getAllWithDetailsByActivityType(activityType),
            clothingItemDao.getByActivityType(activityType)
        ) { entries, tagged -> TemperatureChartPolicy.build(entries, tagged, useApparent) }

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
