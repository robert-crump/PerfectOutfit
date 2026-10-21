package com.example.perfectoutfit.feature.widget

import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.OutfitEntry
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.ThemeMode
import com.example.perfectoutfit.core.model.WeatherSnapshot
import com.example.perfectoutfit.feature.forecast.ForecastLocation
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class OutfitWidgetTest {

    private val day = LocalDate.of(2026, 10, 21)
    private val now = day.atTime(14, 35)
    private val clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val hamburg = ForecastLocation(53.55, 9.99, "Hamburg")

    private val entryDao = FakeOutfitEntryDao()
    private val preferences = PreferencesManager(FakePreferencesDataStore())
    private val store = WidgetForecastStore(FakePreferencesDataStore(), Json, clock)
    private val content = OutfitWidgetContent(store, preferences, Recommendations(entryDao), clock)

    private fun hour(time: LocalDateTime, real: Double, apparent: Double = real) = HourlyWeather(
        time = time,
        temperatureCelsius = real,
        apparentTemperatureCelsius = apparent,
        windSpeedKmh = 0.0,
        windDirectionDegrees = 0,
        uvIndex = 0,
        cloudCoverPercent = 0,
        precipitationProbabilityPercent = 0
    )

    private fun rated(id: Long, activityType: ActivityType, apparent: Double) = OutfitEntryWithDetails(
        entry = OutfitEntry(
            id = id, weatherSnapshotId = id, activityType = activityType, comfortRating = 0, createdAt = 0
        ),
        weatherSnapshot = WeatherSnapshot(
            id = id, timestamp = 0, latitude = 0.0, longitude = 0.0, locationName = "",
            temperatureCelsius = apparent, apparentTemperatureCelsius = apparent, windSpeedKmh = 0.0,
            windDirectionDegrees = 0, uvIndex = 0, cloudCoverPercent = 0, precipitationProbabilityPercent = 0
        ),
        clothingItems = emptyList()
    )

    // ── Binder ───────────────────────────────────────────────────────────────

    @Test
    fun `binds the rounded reference temperature, its basis and the place`() {
        val binding = bindOutfitWidget("Hamburg", hour(now, real = 12.4, apparent = 9.6), useApparent = true, emptySet())

        assertEquals("10°", binding.valueText)
        assertEquals("Feels like", binding.basisText)
        assertEquals("Hamburg", binding.placeText)
        assertEquals("Feels like · Hamburg", binding.combinedLine)
        assertTrue(binding.iconsOpenActivityType)
    }

    @Test
    fun `real temperature basis uses the dry-bulb value`() {
        val binding = bindOutfitWidget("Hamburg", hour(now, real = 12.4, apparent = 9.6), useApparent = false, emptySet())

        assertEquals("12°", binding.valueText)
        assertEquals("Real temp.", binding.basisText)
    }

    @Test
    fun `icons are bike commute then walking, lit only when recommended`() {
        val binding = bindOutfitWidget("Hamburg", hour(now, 10.0), true, setOf(ActivityType.WALKING))

        assertEquals(
            listOf(
                WidgetActivityIcon(ActivityType.BIKE_COMMUTE, lit = false),
                WidgetActivityIcon(ActivityType.WALKING, lit = true)
            ),
            binding.icons
        )
    }

    @Test
    fun `no saved place asks to open the app and opens Home's first step`() {
        val binding = bindOutfitWidget(null, null, true, emptySet())

        assertEquals(NO_TEMPERATURE_TEXT, binding.valueText)
        assertNull(binding.basisText)
        assertEquals(NO_LOCATION_TEXT, binding.placeText)
        assertEquals(NO_LOCATION_TEXT, binding.combinedLine)
        assertFalse(binding.iconsOpenActivityType)
        assertTrue(binding.icons.none { it.lit })
    }

    @Test
    fun `saved place without the current hour shows a dash and dims both icons`() {
        val binding = bindOutfitWidget("Hamburg", null, true, setOf(ActivityType.WALKING))

        assertEquals(NO_TEMPERATURE_TEXT, binding.valueText)
        assertEquals("Hamburg", binding.placeText)
        assertTrue(binding.icons.none { it.lit })
        assertTrue(binding.iconsOpenActivityType)
    }

    // ── Content ──────────────────────────────────────────────────────────────

    @Test
    fun `nothing saved binds the no-location state`() = runTest {
        assertEquals(NO_LOCATION_TEXT, content.load().placeText)
    }

    @Test
    fun `lights the activity types with a recommendation at the current hour`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), 10.0), hour(day.atTime(15, 0), 20.0)))
        entryDao.ratedEntries = listOf(rated(1, ActivityType.WALKING, apparent = 11.0))

        val binding = content.load()

        assertEquals("10°", binding.valueText)
        assertEquals("Hamburg", binding.placeText)
        assertEquals(listOf(false, true), binding.icons.map { it.lit })
    }

    @Test
    fun `follows the apparent-vs-real preference`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), real = 10.0, apparent = 4.0)))
        preferences.setUseApparentTemperature(false)

        val binding = content.load()

        assertEquals("10°", binding.valueText)
        assertEquals("Real temp.", binding.basisText)
    }

    @Test
    fun `follows the theme chosen in Settings`() = runTest {
        assertEquals(ThemeMode.SYSTEM, content.load().themeMode)

        preferences.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, content.load().themeMode)
    }

    @Test
    fun `saved hours that end before now give a dash`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), 10.0)))
        val later = Clock.fixed(day.atTime(16, 5).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        entryDao.ratedEntries = listOf(rated(1, ActivityType.WALKING, apparent = 10.0))

        val binding = OutfitWidgetContent(store, preferences, Recommendations(entryDao), later).load()

        assertEquals(NO_TEMPERATURE_TEXT, binding.valueText)
        assertEquals("Hamburg", binding.placeText)
        assertTrue(binding.icons.none { it.lit })
    }

    // ── Store ────────────────────────────────────────────────────────────────

    @Test
    fun `store keeps only hours from the current clock hour on`() = runTest {
        store.save(hamburg, (10..16).map { hour(day.atTime(it, 0), it.toDouble()) })

        val saved = store.load()!!

        assertEquals(hamburg, saved.location)
        assertEquals((14..16).map { day.atTime(it, 0) }, saved.hours.map { it.time })
        assertEquals(clock.millis(), saved.fetchedAtMs)
    }

    @Test
    fun `a refetch for an old place never overwrites a newer one`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), 10.0)))
        val berlin = ForecastLocation(52.5, 13.4, "Berlin")
        store.save(berlin, listOf(hour(day.atTime(14, 0), 5.0)))

        store.saveIfStillAt(hamburg, listOf(hour(day.atTime(14, 0), 30.0)))

        val saved = store.load()!!
        assertEquals(berlin, saved.location)
        assertEquals(5.0, saved.hours.single().temperatureCelsius, 0.0)
    }

    @Test
    fun `a refetch for the saved place replaces its hours`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), 10.0)))

        store.saveIfStillAt(hamburg, listOf(hour(day.atTime(14, 0), 30.0)))

        assertEquals(30.0, store.load()!!.hours.single().temperatureCelsius, 0.0)
    }

    @Test
    fun `hourAt matches the clock hour exactly`() {
        val forecast = WidgetForecast(hamburg, listOf(hour(day.atTime(14, 0), 10.0)), 0)

        assertEquals(10.0, forecast.hourAt(day.atTime(14, 59))!!.temperatureCelsius, 0.0)
        assertNull(forecast.hourAt(day.atTime(15, 0)))
    }
}
