package com.example.perfectoutfit.feature.widget

import com.example.perfectoutfit.core.location.LocationPermissions
import com.example.perfectoutfit.core.location.LocationResult
import com.example.perfectoutfit.core.location.LocationSource
import com.example.perfectoutfit.feature.forecast.ForecastLocation
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.testutil.FakeOpenMeteoApi
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class WidgetForecastRefreshTest {

    private val day = LocalDate.of(2026, 10, 25)
    private val clock = Clock.fixed(day.atTime(9, 0).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val hours = (0..23).map { day.atTime(it, 0) }
    private val aachen = ForecastLocation(50.78, 6.08, "Aachen")
    private val cologne = ForecastLocation(50.94, 6.96, "Köln")

    private val api = FakeOpenMeteoApi(hours)
    private val store = WidgetForecastStore(FakePreferencesDataStore(), Json, clock)
    private val widgetUpdater = FakeWidgetUpdater()

    private fun refresh(background: Boolean, device: LocationResult) = WidgetForecastRefresh(
        api, store, widgetUpdater,
        locationSource = object : LocationSource {
            override suspend fun current() = device
        },
        locationPermissions = object : LocationPermissions {
            override fun hasForeground() = true
            override fun hasBackground() = background
        }
    )

    private fun found(location: ForecastLocation) = LocationResult.Found(location.lat, location.lon, location.name)

    private suspend fun saveAt(location: ForecastLocation) = store.save(location, emptyList<HourlyWeather>())

    @Test
    fun `with background location the widget follows the device`() = runTest {
        saveAt(aachen)

        refresh(background = true, device = found(cologne)).run()

        assertEquals(listOf(cologne.lat to cologne.lon), api.forecastCoordinates)
        assertEquals(cologne, store.load()?.location)
        assertEquals(15, store.load()?.hours?.size)
        assertEquals(1, widgetUpdater.redraws)
    }

    @Test
    fun `with background location and nothing saved yet the device's place is saved`() = runTest {
        refresh(background = true, device = found(cologne)).run()

        assertEquals(cologne, store.load()?.location)
    }

    @Test
    fun `without background location the saved place is refetched`() = runTest {
        saveAt(aachen)

        refresh(background = false, device = found(cologne)).run()

        assertEquals(listOf(aachen.lat to aachen.lon), api.forecastCoordinates)
        assertEquals(aachen, store.load()?.location)
        assertEquals(15, store.load()?.hours?.size)
    }

    @Test
    fun `when the device has no location the saved place is refetched`() = runTest {
        saveAt(aachen)

        refresh(background = true, device = LocationResult.Unavailable("no fix")).run()

        assertEquals(listOf(aachen.lat to aachen.lon), api.forecastCoordinates)
        assertEquals(aachen, store.load()?.location)
    }

    @Test
    fun `with neither a device location nor a saved place nothing happens`() = runTest {
        refresh(background = false, device = found(cologne)).run()

        assertEquals(0, api.forecastCalls)
        assertNull(store.load())
        assertEquals(0, widgetUpdater.redraws)
    }

    @Test
    fun `a failed fetch keeps the saved place and still redraws`() = runTest {
        saveAt(aachen)
        api.failNext = true

        refresh(background = true, device = found(cologne)).run()

        assertEquals(aachen, store.load()?.location)
        assertEquals(1, widgetUpdater.redraws)
    }
}
