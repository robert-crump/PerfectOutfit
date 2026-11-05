package com.example.perfectoutfit.feature.outfit

import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.feature.forecast.ForecastLocation
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentPlacesTest {

    private fun snapshot(name: String, lat: Double, lon: Double) = WeatherSnapshot(
        timestamp = 0, latitude = lat, longitude = lon, locationName = name,
        temperatureCelsius = 0.0, apparentTemperatureCelsius = 0.0, windSpeedKmh = 0.0,
        windDirectionDegrees = 0, uvIndex = 0, cloudCoverPercent = 0, precipitationProbabilityPercent = 0
    )

    private val home = ForecastLocation(50.78, 6.08, "Aachen")

    private fun names(places: List<RecentPlace>) = places.map { it.location.name }

    @Test
    fun `places are listed newest first, one per town`() {
        val places = recentPlaces(
            listOf(
                snapshot("Cologne", 50.94, 6.96),
                snapshot("Bonn", 50.73, 7.10),
                snapshot("Cologne", 50.93, 6.95)
            ),
            home
        )
        assertEquals(listOf("Cologne", "Bonn"), names(places))
    }

    @Test
    fun `the newest coordinates of a town win`() {
        val places = recentPlaces(
            listOf(snapshot("Cologne", 50.94, 6.96), snapshot("Cologne", 50.90, 6.90)),
            home
        )
        assertEquals(LogLocation("Cologne", 50.94, 6.96), places.single().location)
    }

    @Test
    fun `blank, default-named and unresolved entries are skipped`() {
        val places = recentPlaces(
            listOf(
                snapshot("", 50.94, 6.96),
                snapshot(LogLocation.DEFAULT_NAME, 50.94, 6.96),
                snapshot("Nowhere", 0.0, 0.0),
                snapshot("Bonn", 50.73, 7.10)
            ),
            home
        )
        assertEquals(listOf("Bonn"), names(places))
    }

    @Test
    fun `the place the user is in now is hidden`() {
        val places = recentPlaces(
            listOf(snapshot("Aachen", 50.77, 6.09), snapshot("Bonn", 50.73, 7.10)),
            home
        )
        assertEquals(listOf("Bonn"), names(places))
    }

    @Test
    fun `at most five places are listed`() {
        val snapshots = (1..7).map { snapshot("Town $it", 40.0 + it, 10.0) }
        assertEquals(5, recentPlaces(snapshots, home).size)
    }

    @Test
    fun `towns sharing a name far apart stay separate and get a distance label`() {
        val places = recentPlaces(
            listOf(
                snapshot("Springfield", 39.80, -89.64), // Illinois
                snapshot("Springfield", 42.10, -72.59), // Massachusetts
                snapshot("Bonn", 50.73, 7.10)
            ),
            home
        )
        assertEquals(listOf("Springfield", "Springfield", "Bonn"), names(places))
        assertEquals(null, places[2].distanceLabel)
        // Both labels are set and differ; the exact distance is the haversine's business.
        val labels = places.take(2).map { it.distanceLabel }
        assert(labels.all { it != null && it.endsWith(" km") }) { labels }
        assert(labels[0] != labels[1]) { labels }
    }

    @Test
    fun `entries of one town within 25 km merge even when far from its center`() {
        // A searched city's center vs. a GPS log at its edge.
        val places = recentPlaces(
            listOf(snapshot("Berlin", 52.52, 13.40), snapshot("Berlin", 52.43, 13.53)),
            home
        )
        assertEquals(1, places.size)
        assertEquals(null, places.single().distanceLabel)
    }

    @Test
    fun `without a current location nothing is hidden and collisions get no label`() {
        val places = recentPlaces(
            listOf(snapshot("Springfield", 39.80, -89.64), snapshot("Springfield", 42.10, -72.59)),
            current = null
        )
        assertEquals(listOf(null, null), places.map { it.distanceLabel })
    }

    @Test
    fun `distance between Aachen and Cologne is about 64 km`() {
        assertEquals(64.0, distanceKm(50.78, 6.08, 50.94, 6.96), 2.0)
    }
}
