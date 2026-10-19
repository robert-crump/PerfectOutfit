package com.example.perfectoutfit.readme

import com.example.perfectoutfit.core.model.ActivityType
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Generated outfit history for the README screenshots: a few rides and runs a week going back
 * [WEEKS] weeks from today, plus some bike commutes and walks, at feels-like temperatures from −2 °C to 24 °C so Explorer has many
 * stops. Each outfit follows a layering rule; most are rated perfect, the rest were dressed one
 * layer too light (too cold) or too heavy (too hot). Fixed seed, so every run is the same.
 */
object DemoHistory {
    const val WEEKS = 12
    private const val SEED = 2026
    private const val CYCLING_COUNT = 35
    private const val RUNNING_COUNT = 15
    private val RUN_SLOTS = setOf(2, 5, 8)
    private const val EVERYDAY_SEED = 2027
    private const val EVERYDAY_COUNT = 16

    data class Outfit(
        val activityType: ActivityType,
        val time: LocalDateTime,
        val feelsLike: Double,
        val windKmh: Double,
        val uv: Int,
        val cloud: Int,
        val rain: Int,
        val items: List<String>,
        /** -1 too cold, 0 perfect, 1 too hot, null not rated yet. */
        val rating: Int?
    ) {
        /** Air temperature, from the same rough wind chill as [DemoForecast]. */
        val temperature: Double get() = ((feelsLike + windKmh * 0.12 + 1.5) * 10).roundToInt() / 10.0
    }

    /**
     * [perfectAt] are (activityType, rounded feels-like °C) pairs that get the newest rated entry at that
     * temperature, rated perfect, so the screenshots' recommendations are outfits that worked.
     */
    fun generate(today: LocalDate, perfectAt: List<Pair<ActivityType, Int>>): List<Outfit> {
        val random = Random(SEED)
        val days = WEEKS * 7
        // Three runs in every ten workouts, interleaved with the rides.
        val generated = List(CYCLING_COUNT + RUNNING_COUNT) { i ->
            if (i % 10 in RUN_SLOTS) ActivityType.RUNNING else ActivityType.CYCLING
        }
        val outfits = generated.mapIndexed { i, activityType ->
            // Spread evenly from WEEKS ago up to a week ago; the last week is for the anchors.
            val daysAgo = 7 + (days - 7) * (generated.size - i) / generated.size
            val hour = random.nextInt(7, 19)
            val feelsLike = random.nextInt(-2, 25) + random.nextInt(-4, 5) / 10.0
            val rating = when (random.nextInt(100)) {
                in 0 until 12 -> -1
                in 12 until 25 -> 1
                else -> 0
            }
            // Too cold: dressed for 4 °C warmer. Too hot: dressed for 4 °C colder.
            val dressedFor = feelsLike - 4 * rating
            outfit(activityType, today.minusDays(daysAgo.toLong()).atTime(hour, 0), feelsLike, dressedFor, rating, random)
        }.sortedBy { it.time }

        val anchors = perfectAt.mapIndexed { i, (activityType, temp) ->
            val time = today.minusDays(2L + i).atTime(17, 0)
            outfit(activityType, time, temp.toDouble(), temp.toDouble(), 0, random, dry = true)
        }
        // Yesterday evening's ride in a shower, waiting to be rated.
        val pending = outfit(ActivityType.CYCLING, today.minusDays(1).atTime(17, 0), 9.0, 9.0, null, random, rain = 70)

        return (outfits + everyday(today) + anchors + pending).sortedBy { it.time }
    }

    /**
     * Commutes and walks, two in three of them commutes, all rated. From their own seed, so
     * adding them left the rides and runs as they were.
     */
    private fun everyday(today: LocalDate): List<Outfit> {
        val random = Random(EVERYDAY_SEED)
        val days = WEEKS * 7
        return List(EVERYDAY_COUNT) { i ->
            val activityType = if (i % 3 == 2) ActivityType.WALKING else ActivityType.BIKE_COMMUTE
            val daysAgo = 7 + (days - 7) * (EVERYDAY_COUNT - i) / EVERYDAY_COUNT
            val hour = if (activityType == ActivityType.BIKE_COMMUTE) random.nextInt(7, 9) else random.nextInt(12, 19)
            val feelsLike = random.nextInt(-2, 20) + random.nextInt(-4, 5) / 10.0
            val rating = if (random.nextInt(100) < 20) random.nextInt(2) * 2 - 1 else 0
            outfit(activityType, today.minusDays(daysAgo.toLong()).atTime(hour, 0), feelsLike, feelsLike - 4 * rating, rating, random)
        }
    }

    private fun outfit(
        activityType: ActivityType,
        time: LocalDateTime,
        feelsLike: Double,
        dressedFor: Double,
        rating: Int?,
        random: Random,
        dry: Boolean = false,
        rain: Int = if (!dry && random.nextInt(5) == 0) random.nextInt(60, 90) else random.nextInt(0, 30)
    ): Outfit {
        val wind = random.nextInt(4, 26).toDouble()
        val uv = if (time.hour in 10..16) (feelsLike / 5).roundToInt().coerceIn(0, 6) else 1
        val items = when (activityType) {
            ActivityType.CYCLING -> cyclingOutfit(dressedFor, rain, uv)
            ActivityType.RUNNING -> runningOutfit(dressedFor, rain)
            ActivityType.BIKE_COMMUTE, ActivityType.WALKING -> everydayOutfit(dressedFor, rain)
        }
        return Outfit(activityType, time, feelsLike, wind, uv, random.nextInt(0, 101), rain, items, rating)
    }

    /** Default catalog names, by the feels-like temperature the rider dressed for. */
    fun cyclingOutfit(t: Double, rain: Int, uv: Int): List<String> = buildList {
        add(if (t < 10) "Thermal Bib Tights" else "Bib Shorts")
        add("Jersey")
        if (t < 8) add("Fleece") else if (t < 15) add("Vest")
        if (rain >= 60) add("Rain Jacket")
        if (t in 10.0..17.0) add("Arm Warmers")
        if (t >= 22 && uv >= 5) add("UV Sleeves")
        add(if (t < 5) "Thermal Socks" else "Socks")
        if (t < 5) add("Thermal Overshoes") else if (t < 10) add("Overshoes")
        if (t < 2) add("Balaclava") else if (t < 12) add("Cap")
        if (t < 5) add("Thick Gloves") else if (t < 12) add("Thin Gloves")
    }

    fun runningOutfit(t: Double, rain: Int): List<String> = buildList {
        add(if (t < 10) "Jogging Pants" else "Shorts")
        add(if (t < 15) "Longsleeve Shirt" else "Shirt")
        if (t < 2) add("Fleece") else if (t < 8) add("Pullover")
        if (rain >= 60) add("Rain Jacket")
        if (t < 0) add("Buff")
        if (t < 3) add("Tuque") else if (t < 8) add("Headband")
        if (t < 6) add("Thin Winter Gloves")
    }

    fun everydayOutfit(t: Double, rain: Int): List<String> = buildList {
        add(
            when {
                rain >= 60 -> "Rain Jacket"
                t < 5 -> "Winter Jacket"
                t < 10 -> "Teddy Jacket"
                else -> "Fleece Jacket"
            }
        )
        if (t < 0) add("Ski Mask")
        if (t < 3) add("Thick Gloves") else if (t < 8) add("Thin Gloves")
    }
}
