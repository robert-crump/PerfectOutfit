package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.referenceTemp
import kotlin.math.abs
import kotlin.math.roundToInt

/** Which temperature basis fits the user's ratings better (see "Mode comparison" in CONTEXT.md). */
data class ModeComparison(
    val overall: ModeComparisonLine,
    val byActivityType: Map<ActivityType, ModeComparisonLine>
)

/**
 * One line of a [ModeComparison]: [pairs] counted, and per mode the contradictions in halves
 * (2 per contradictory pair, 1 per tied pair), so the arithmetic stays exact.
 */
data class ModeComparisonLine(
    val pairs: Int,
    val feelsLikeHalves: Int,
    val actualHalves: Int
) {
    /** Share of contradictory pairs (a tie counts half), 0..1; null without pairs. Lower is better. */
    val feelsLikeScore: Double? get() = score(feelsLikeHalves)
    val actualScore: Double? get() = score(actualHalves)

    val verdict: ModeVerdict
        get() = when {
            pairs < MIN_PAIRS -> ModeVerdict.NOT_ENOUGH_DATA
            // gap >= MIN_GAP_PERCENT points, i.e. |Δhalves| / (2 · pairs) >= MIN_GAP_PERCENT / 100
            abs(feelsLikeHalves - actualHalves) * 100 < MIN_GAP_PERCENT * 2 * pairs -> ModeVerdict.NO_CLEAR_DIFFERENCE
            feelsLikeHalves < actualHalves -> ModeVerdict.FEELS_LIKE_BETTER
            else -> ModeVerdict.ACTUAL_BETTER
        }

    operator fun plus(other: ModeComparisonLine) = ModeComparisonLine(
        pairs + other.pairs,
        feelsLikeHalves + other.feelsLikeHalves,
        actualHalves + other.actualHalves
    )

    private fun score(halves: Int): Double? = if (pairs == 0) null else halves / (2.0 * pairs)

    companion object {
        const val MIN_PAIRS = 20
        const val MIN_GAP_PERCENT = 10
        val EMPTY = ModeComparisonLine(0, 0, 0)
    }
}

enum class ModeVerdict { NOT_ENOUGH_DATA, NO_CLEAR_DIFFERENCE, FEELS_LIKE_BETTER, ACTUAL_BETTER }

internal object ModeComparisonPolicy {

    /**
     * Pairs are two rated entries of one activity type with exactly the same (non-empty) item
     * set and different ratings. Per mode a pair is consistent when the warmer entry has the
     * warmer rating, contradictory when it is the other way round, and tied when both round
     * to the same degree.
     */
    fun compare(entries: List<OutfitEntryWithDetails>): ModeComparison {
        val byActivityType = ActivityType.entries.associateWith { activityType ->
            entries
                .filter { it.entry.activityType == activityType && it.entry.comfortRating != null }
                .groupBy { entry -> entry.clothingItems.map { it.id }.toSet() }
                .filterKeys { it.isNotEmpty() }
                .values
                .fold(ModeComparisonLine.EMPTY) { line, sameOutfit -> line + compareOutfit(sameOutfit) }
        }
        return ModeComparison(
            overall = byActivityType.values.fold(ModeComparisonLine.EMPTY, ModeComparisonLine::plus),
            byActivityType = byActivityType
        )
    }

    private fun compareOutfit(entries: List<OutfitEntryWithDetails>): ModeComparisonLine {
        var pairs = 0
        var feelsLikeHalves = 0
        var actualHalves = 0
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val a = entries[i]
                val b = entries[j]
                val ratingOrder = a.entry.comfortRating!!.compareTo(b.entry.comfortRating!!)
                if (ratingOrder == 0) continue
                pairs++
                feelsLikeHalves += contradictionHalves(a, b, ratingOrder, useApparent = true)
                actualHalves += contradictionHalves(a, b, ratingOrder, useApparent = false)
            }
        }
        return ModeComparisonLine(pairs, feelsLikeHalves, actualHalves)
    }

    /** 0 consistent, 1 tied, 2 contradictory. */
    private fun contradictionHalves(
        a: OutfitEntryWithDetails,
        b: OutfitEntryWithDetails,
        ratingOrder: Int,
        useApparent: Boolean
    ): Int {
        val tempOrder = a.roundedTemp(useApparent).compareTo(b.roundedTemp(useApparent))
        return when {
            tempOrder == 0 -> 1
            tempOrder.sign() == ratingOrder.sign() -> 0
            else -> 2
        }
    }

    private fun Int.sign(): Int = compareTo(0)

    private fun OutfitEntryWithDetails.roundedTemp(useApparent: Boolean): Int =
        weatherSnapshot.referenceTemp(useApparent).roundToInt()
}
