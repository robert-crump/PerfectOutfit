package com.example.perfectoutfit.readme

import com.example.perfectoutfit.core.model.Sport
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Generated outfit history for the README screenshots: a few rides and runs a week going back
 * [WEEKS] weeks from today, at feels-like temperatures from −2 °C to 24 °C so Explorer has many
 * stops. Each outfit follows a layering rule; most are rated perfect, the rest were dressed one
 * layer too light (too cold) or too heavy (too hot). Fixed seed, so every run is the same.
 */
object DemoHistory {
    const val WEEKS = 12
    private const val SEED = 2026
    private const val CYCLING_COUNT = 35
    private const val RUNNING_COUNT = 15

    data class Outfit(
        val sport: Sport,
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
     * [perfectAt] are (sport, rounded feels-like °C) pairs that get the newest rated entry at that
     * temperature, rated perfect, so the screenshots' recommendations are outfits that worked.
     */
    fun generate(today: LocalDate, perfectAt: List<Pair<Sport, Int>>): List<Outfit> {
        val random = Random(SEED)
        val days = WEEKS * 7
        val generated = List(CYCLING_COUNT) { Sport.CYCLING } + List(RUNNING_COUNT) { Sport.RUNNING }
        val outfits = generated.mapIndexed { i, sport ->
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
            outfit(sport, today.minusDays(daysAgo.toLong()).atTime(hour, 0), feelsLike, dressedFor, rating, random)
        }.sortedBy { it.time }

        val anchors = perfectAt.mapIndexed { i, (sport, temp) ->
            val time = today.minusDays(2L + i).atTime(17, 0)
            outfit(sport, time, temp.toDouble(), temp.toDouble(), 0, random)
        }
        // Yesterday evening's ride, waiting to be rated.
        val pending = outfit(Sport.CYCLING, today.minusDays(1).atTime(17, 0), 9.0, 9.0, null, random)

        return (outfits + anchors + pending).sortedBy { it.time }
    }

    private fun outfit(
        sport: Sport,
        time: LocalDateTime,
        feelsLike: Double,
        dressedFor: Double,
        rating: Int?,
        random: Random
    ): Outfit {
        val wind = random.nextInt(4, 26).toDouble()
        val rain = if (random.nextInt(5) == 0) random.nextInt(60, 90) else random.nextInt(0, 30)
        val uv = if (time.hour in 10..16) (feelsLike / 5).roundToInt().coerceIn(0, 6) else 1
        val items = when (sport) {
            Sport.CYCLING -> cyclingOutfit(dressedFor, rain, uv)
            Sport.RUNNING -> runningOutfit(dressedFor, rain)
        }
        return Outfit(sport, time, feelsLike, wind, uv, random.nextInt(0, 101), rain, items, rating)
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
}
