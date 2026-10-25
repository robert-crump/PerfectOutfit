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
    fun `binds the rounded reference temperature and its caption`() {
        val binding = bindOutfitWidget(
            ActivityType.WALKING, "Hamburg", hour(now, real = 12.4, apparent = 9.6), useApparent = true, recommended = true
        )

        assertEquals(ActivityType.WALKING, binding.activityType)
        assertEquals("10°", binding.valueText)
        assertEquals("feels", binding.captionText)
        assertEquals("Walking, 10° feels", binding.contentDescription)
        assertTrue(binding.lit)
        assertTrue(binding.opensActivityType)
    }

    @Test
    fun `real temperature caption uses the dry-bulb value`() {
        val binding = bindOutfitWidget(
            ActivityType.WALKING, "Hamburg", hour(now, real = 12.4, apparent = 9.6), useApparent = false, recommended = false
        )

        assertEquals("12°", binding.valueText)
        assertEquals("actual", binding.captionText)
        assertFalse(binding.lit)
    }

    @Test
    fun `no saved place asks to open the app and opens Home's first step`() {
        val binding = bindOutfitWidget(ActivityType.CYCLING, null, null, true, recommended = false)

        assertEquals(NO_TEMPERATURE_TEXT, binding.valueText)
        assertEquals(NO_LOCATION_TEXT, binding.captionText)
        assertFalse(binding.lit)
        assertFalse(binding.opensActivityType)
    }

    @Test
    fun `saved place without the current hour shows a dash, keeps the caption and dims the icon`() {
        val binding = bindOutfitWidget(ActivityType.WALKING, "Hamburg", null, true, recommended = true)

        assertEquals(NO_TEMPERATURE_TEXT, binding.valueText)
        assertEquals("feels", binding.captionText)
        assertFalse(binding.lit)
        assertTrue(binding.opensActivityType)
    }

    // ── Content ──────────────────────────────────────────────────────────────

    @Test
    fun `nothing saved binds the no-location state`() = runTest {
        assertEquals(NO_LOCATION_TEXT, content.load(ActivityType.WALKING).captionText)
    }

    @Test
    fun `lights only the activity type with a recommendation at the current hour`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), 10.0), hour(day.atTime(15, 0), 20.0)))
        entryDao.ratedEntries = listOf(rated(1, ActivityType.WALKING, apparent = 11.0))

        val walking = content.load(ActivityType.WALKING)
        val commute = content.load(ActivityType.BIKE_COMMUTE)

        assertEquals("10°", walking.valueText)
        assertTrue(walking.lit)
        assertFalse(commute.lit)
    }

    @Test
    fun `follows the apparent-vs-real preference`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), real = 10.0, apparent = 4.0)))
        preferences.setUseApparentTemperature(false)

        val binding = content.load(ActivityType.WALKING)

        assertEquals("10°", binding.valueText)
        assertEquals("actual", binding.captionText)
    }

    @Test
    fun `follows the theme chosen in Settings`() = runTest {
        assertEquals(ThemeMode.SYSTEM, content.load(ActivityType.WALKING).themeMode)

        preferences.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, content.load(ActivityType.WALKING).themeMode)
    }

    @Test
    fun `saved hours that end before now give a dash`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), 10.0)))
        val later = Clock.fixed(day.atTime(16, 5).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        entryDao.ratedEntries = listOf(rated(1, ActivityType.WALKING, apparent = 10.0))

        val binding = OutfitWidgetContent(store, preferences, Recommendations(entryDao), later)
            .load(ActivityType.WALKING)

        assertEquals(NO_TEMPERATURE_TEXT, binding.valueText)
        assertFalse(binding.lit)
    }

    // ── Grid ─────────────────────────────────────────────────────────────────

    @Test
    fun `grid binds every activity type in order and lights only those with a recommendation`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), 10.0)))
        entryDao.ratedEntries = listOf(
            rated(1, ActivityType.CYCLING, apparent = 10.0),
            rated(2, ActivityType.WALKING, apparent = 11.0)
        )

        val bindings = content.loadAll()

        assertEquals(ActivityType.entries, bindings.map { it.activityType })
        assertEquals(listOf(true, false, false, true), bindings.map { it.lit })
        assertTrue(bindings.all { it.opensActivityType })
    }

    @Test
    fun `grid with nothing saved is all faint and opens Home's first step`() = runTest {
        val bindings = content.loadAll()

        assertTrue(bindings.none { it.lit })
        assertTrue(bindings.none { it.opensActivityType })
    }

    @Test
    fun `grid whose saved hours don't cover now is all faint but still opens each result step`() = runTest {
        store.save(hamburg, listOf(hour(day.atTime(14, 0), 10.0)))
        val later = Clock.fixed(day.atTime(16, 5).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        entryDao.ratedEntries = listOf(rated(1, ActivityType.WALKING, apparent = 10.0))

        val bindings = OutfitWidgetContent(store, preferences, Recommendations(entryDao), later).loadAll()

        assertTrue(bindings.none { it.lit })
        assertTrue(bindings.all { it.opensActivityType })
    }

    @Test
    fun `grid tiles tell TalkBack whether there's a recommendation now`() {
        val current = hour(now, 10.0)

        assertEquals(
            "Cycling, recommendation now",
            bindOutfitWidget(ActivityType.CYCLING, "Hamburg", current, true, recommended = true).gridContentDescription
        )
        assertEquals(
            "Running, no recommendation now",
            bindOutfitWidget(ActivityType.RUNNING, "Hamburg", current, true, recommended = false).gridContentDescription
        )
        assertEquals(
            "Walking, open app",
            bindOutfitWidget(ActivityType.WALKING, null, null, true, recommended = false).gridContentDescription
        )
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
