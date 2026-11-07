package com.example.perfectoutfit.feature.recommendation

import com.example.perfectoutfit.core.model.BODY_PART_DISPLAY_ORDER
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.ComfortRating
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.referenceTemp
import kotlin.math.roundToInt

/** What rated history says about one rounded degree of the chart's axis. */
enum class DegreeCoverage {
    /** At least one entry at this degree is rated perfect. */
    PERFECT,
    /** Rated entries exist here, none of them perfect. */
    RATED,
    /** Nothing rated at this degree. */
    NONE
}

/** One cell of the coverage strip. */
data class DegreeCell(val temp: Int, val coverage: DegreeCoverage, val perfectOutfits: Int)

/** An item's row: the distinct rounded degrees at which it was in a perfect outfit, ascending. */
data class ItemRange(val item: ClothingItem, val perfectTemps: List<Int>) {
    val minTemp: Int get() = perfectTemps.first()
    val maxTemp: Int get() = perfectTemps.last()
    fun spans(temp: Int): Boolean = temp in minTemp..maxTemp
}

data class BodyPartRanges(val bodyPart: BodyPart, val rows: List<ItemRange>)

/**
 * Explorer's temperature-range chart for one activity type (see "Temperature chart" in CONTEXT.md).
 * [degrees] runs from the coldest to the warmest rated degree, one cell each, and is empty without
 * rated history.
 */
data class TemperatureChart(
    val degrees: List<DegreeCell>,
    val groups: List<BodyPartRanges>
) {
    val minTemp: Int? get() = degrees.firstOrNull()?.temp
    val maxTemp: Int? get() = degrees.lastOrNull()?.temp
    val hasRatedHistory: Boolean get() = degrees.isNotEmpty()
    fun cellAt(temp: Int): DegreeCell? = minTemp?.let { degrees.getOrNull(temp - it) }
}

internal object TemperatureChartPolicy {

    /**
     * [entries] are the activity type's entries (unrated ones are ignored); [taggedItems] are the
     * items currently tagged for it. Only tagged items get rows; the strip counts every perfect
     * outfit, whatever its items.
     */
    fun build(
        entries: List<OutfitEntryWithDetails>,
        taggedItems: List<ClothingItem>,
        useApparent: Boolean
    ): TemperatureChart {
        val rated = entries.filter { it.entry.rating != null }
        val perfect = rated.filter { it.entry.rating == ComfortRating.PERFECT }

        val degrees = if (rated.isEmpty()) emptyList() else {
            val ratedTemps = rated.map { it.roundedTemp(useApparent) }.toSet()
            val perfectCounts = perfect.groupingBy { it.roundedTemp(useApparent) }.eachCount()
            (ratedTemps.min()..ratedTemps.max()).map { temp ->
                val count = perfectCounts[temp] ?: 0
                val coverage = when {
                    count > 0 -> DegreeCoverage.PERFECT
                    temp in ratedTemps -> DegreeCoverage.RATED
                    else -> DegreeCoverage.NONE
                }
                DegreeCell(temp, coverage, count)
            }
        }

        val perfectTempsById = mutableMapOf<Long, MutableSet<Int>>()
        perfect.forEach { entry ->
            val temp = entry.roundedTemp(useApparent)
            entry.clothingItems.forEach { perfectTempsById.getOrPut(it.id) { mutableSetOf() } += temp }
        }

        val tagged = taggedItems.distinctBy { it.id }
        val ranges = tagged.mapNotNull { item ->
            perfectTempsById[item.id]?.let { ItemRange(item, it.sorted()) }
        }
        val groups = BODY_PART_DISPLAY_ORDER.mapNotNull { part ->
            ranges.filter { it.item.bodyPart == part }
                .sortedWith(compareBy<ItemRange>({ it.minTemp }, { it.maxTemp }, { it.item.name }))
                .takeIf { it.isNotEmpty() }
                ?.let { BodyPartRanges(part, it) }
        }
        return TemperatureChart(degrees, groups)
    }

    private fun OutfitEntryWithDetails.roundedTemp(useApparent: Boolean): Int =
        weatherSnapshot.referenceTemp(useApparent).roundToInt()
}
