package com.example.perfectoutfit.feature.outfit

import com.example.perfectoutfit.core.model.Place

import com.example.perfectoutfit.core.model.WeatherSnapshot
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A place an earlier outfit was logged at. [distanceLabel] ("840 km", from the current location)
 * is set only when another listed place shares its name, to tell the two apart.
 */
data class RecentPlace(val location: Place, val distanceLabel: String? = null)

/** Entries of one name closer than this are the same place (a searched city center vs. a GPS log). */
const val SAME_PLACE_KM = 25.0

const val MAX_RECENT_PLACES = 5

/**
 * The places of [snapshotsNewestFirst], most recently used first: one per town (same name within
 * [SAME_PLACE_KM], the newest coordinates winning), without unnamed or unresolved entries and
 * without the [current] place, at most [limit].
 */
fun recentPlaces(
    snapshotsNewestFirst: List<WeatherSnapshot>,
    current: Place?,
    limit: Int = MAX_RECENT_PLACES
): List<RecentPlace> {
    val places = mutableListOf<Place>()
    for (s in snapshotsNewestFirst) {
        val name = s.locationName.trim()
        if (name.isEmpty() || name.equals(Place.DEFAULT_NAME, ignoreCase = true)) continue
        if (s.latitude == 0.0 && s.longitude == 0.0) continue
        val place = Place(s.latitude, s.longitude, name)
        if (places.none { it.isSamePlaceAs(place) }) places += place
    }
    val hereNow = current
    val shown = places.filterNot { hereNow != null && it.isSamePlaceAs(hereNow) }.take(limit)

    return shown.map { place ->
        val collides = shown.count { it.name.equals(place.name, ignoreCase = true) } > 1
        val label = if (collides && current != null) {
            "${distanceKm(current.lat, current.lon, place.lat, place.lon).roundToInt()} km"
        } else null
        RecentPlace(place, label)
    }
}

private fun Place.isSamePlaceAs(other: Place): Boolean =
    name.equals(other.name, ignoreCase = true) && distanceKm(lat, lon, other.lat, other.lon) < SAME_PLACE_KM

/** Great-circle distance in km (haversine). */
fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
    return 2 * EARTH_RADIUS_KM * asin(sqrt(a))
}

private const val EARTH_RADIUS_KM = 6371.0
