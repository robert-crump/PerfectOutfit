package com.example.perfectoutfit.feature.forecast

import com.example.perfectoutfit.core.model.Place

import com.example.perfectoutfit.core.network.OpenMeteoApi
import com.example.perfectoutfit.core.time.currentHour
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.home.WeatherMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Hours of one day at the [place] they were fetched for. */
data class PlacedHours(val place: Place, val hours: List<HourlyWeather>)

/**
 * The known hours for the current location. Owns the hour cache, the display window, per-date
 * fetch-and-merge and the location name. Location acquisition is not its concern: callers
 * hand it a resolved location through [refresh].
 */
@Singleton
class Forecast @Inject constructor(
    private val api: OpenMeteoApi,
    private val clock: Clock
) {
    private data class State(
        val location: Place? = null,
        val hours: List<HourlyWeather> = emptyList(),
        val fetchedAt: Instant? = null
    )

    private val state = MutableStateFlow(State())

    /** The current location, or null while none has been resolved yet. */
    val location: Place? get() = state.value.location

    val locationFlow: Flow<Place?> = state.map { it.location }.distinctUntilChanged()

    /** Every known hour, sorted by time. */
    val hours: Flow<List<HourlyWeather>> = state.map { it.hours }.distinctUntilChanged()

    fun today(): LocalDate = LocalDate.now(clock)

    /** Replaces the forecast with a fresh fetch (past 2 days + today + tomorrow). Throws on failure. */
    suspend fun refresh(lat: Double, lon: Double, name: String) {
        val fetched = fetchAt(lat, lon)
        state.value = State(Place(lat, lon, name), fetched, clock.instant())
    }

    /**
     * Fetches the forecast hours (past 2 days + today + tomorrow) at a place without touching this
     * forecast; the widget refresh uses it for its own cache. Throws on failure.
     */
    suspend fun fetchAt(lat: Double, lon: Double): List<HourlyWeather> =
        WeatherMapper.extractAllHours(api.getForecast(lat, lon))

    /** True when nothing was fetched yet or the last [refresh] is older than [STALE_AFTER]. */
    fun isStale(): Boolean {
        val fetchedAt = state.value.fetchedAt ?: return true
        return Duration.between(fetchedAt, clock.instant()) >= STALE_AFTER
    }

    /** 24 hours starting at the current clock hour; empty if the known hours end before it. */
    fun displayWindow(): List<HourlyWeather> {
        val all = state.value.hours
        val currentHour = clock.currentHour()
        val start = all.indexOfFirst { !it.time.isBefore(currentHour) }
        if (start < 0) return emptyList()
        return all.subList(start, minOf(start + WINDOW_HOURS, all.size))
    }

    /**
     * Hours of [date] at the current location, with that location: fetched and merged into the
     * forecast if none are known yet. Null without fetching while the location is unknown.
     * Throws if the fetch fails.
     */
    suspend fun hoursFor(date: LocalDate): PlacedHours? {
        val known = state.value
        val location = known.location ?: return null
        known.hours.filter { it.time.toLocalDate() == date }
            .let { if (it.isNotEmpty()) return PlacedHours(location, it) }

        val day = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val fetched = WeatherMapper.extractAllHours(
            api.getWeatherForDateRange(location.lat, location.lon, day, day)
        )
        val fetchedDay = PlacedHours(location, fetched.filter { it.time.toLocalDate() == date })
        state.value = state.value.let { current ->
            // A refresh may have swapped the location while we were fetching; drop stale data.
            if (current.location != location) return fetchedDay
            current.copy(
                hours = (current.hours.filter { it.time.toLocalDate() != date } + fetched)
                    .sortedBy { it.time }
            )
        }
        return fetchedDay
    }

    /**
     * Hours of [date] at another place (a past outfit logged elsewhere). Always fetches and never
     * touches the forecast, so Home keeps showing the current location. Throws if the fetch fails.
     */
    suspend fun hoursAt(lat: Double, lon: Double, date: LocalDate): List<HourlyWeather> {
        val day = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        return WeatherMapper.extractAllHours(api.getWeatherForDateRange(lat, lon, day, day))
            .filter { it.time.toLocalDate() == date }
    }

    companion object {
        const val WINDOW_HOURS = 24

        /** How old a forecast may get before Home fetches a new one on its way back to the front. */
        val STALE_AFTER: Duration = Duration.ofMinutes(30)

        /** Exact clock hour, else the last hour before it, else the first hour. Null if empty. */
        fun hourNearest(hours: List<HourlyWeather>, clockHour: Int): HourlyWeather? =
            hours.firstOrNull { it.time.hour == clockHour }
                ?: hours.lastOrNull { it.time.hour < clockHour }
                ?: hours.firstOrNull()
    }
}
