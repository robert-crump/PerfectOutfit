package com.example.perfectoutfit.feature.forecast

import com.example.perfectoutfit.core.network.OpenMeteoApi
import com.example.perfectoutfit.feature.home.HourlyWeather
import com.example.perfectoutfit.feature.home.WeatherMapper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ForecastLocation(val lat: Double, val lon: Double, val name: String)

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
        val location: ForecastLocation? = null,
        val hours: List<HourlyWeather> = emptyList()
    )

    private val state = MutableStateFlow(State())

    /** The current location, or null while none has been resolved yet. */
    val location: ForecastLocation? get() = state.value.location

    val locationFlow: Flow<ForecastLocation?> = state.map { it.location }.distinctUntilChanged()

    /** Every known hour, sorted by time. */
    val hours: Flow<List<HourlyWeather>> = state.map { it.hours }.distinctUntilChanged()

    fun today(): LocalDate = LocalDate.now(clock)

    /** Replaces the forecast with a fresh fetch (past 2 days + today + tomorrow). Throws on failure. */
    suspend fun refresh(lat: Double, lon: Double, name: String) {
        val fetched = WeatherMapper.extractAllHours(api.getForecast(lat, lon))
        state.value = State(ForecastLocation(lat, lon, name), fetched)
    }

    /** 24 hours starting at the current clock hour; empty if the known hours end before it. */
    fun displayWindow(): List<HourlyWeather> {
        val all = state.value.hours
        val currentHour = LocalDateTime.now(clock).withMinute(0).withSecond(0).withNano(0)
        val start = all.indexOfFirst { !it.time.isBefore(currentHour) }
        if (start < 0) return emptyList()
        return all.subList(start, minOf(start + WINDOW_HOURS, all.size))
    }

    /**
     * Hours of [date], fetching and merging them into the forecast if none are known yet.
     * Returns empty without fetching while the location is unknown. Throws if the fetch fails.
     */
    suspend fun hoursFor(date: LocalDate): List<HourlyWeather> {
        val known = state.value
        known.hours.filter { it.time.toLocalDate() == date }.let { if (it.isNotEmpty()) return it }
        val location = known.location ?: return emptyList()

        val day = date.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val fetched = WeatherMapper.extractAllHours(
            api.getWeatherForDateRange(location.lat, location.lon, day, day)
        )
        state.value = state.value.let { current ->
            // A refresh may have swapped the location while we were fetching; drop stale data.
            if (current.location != location) return fetched.filter { it.time.toLocalDate() == date }
            current.copy(
                hours = (current.hours.filter { it.time.toLocalDate() != date } + fetched)
                    .sortedBy { it.time }
            )
        }
        return fetched.filter { it.time.toLocalDate() == date }
    }

    companion object {
        const val WINDOW_HOURS = 24

        /** Exact clock hour, else the last hour before it, else the first hour. Null if empty. */
        fun hourNearest(hours: List<HourlyWeather>, clockHour: Int): HourlyWeather? =
            hours.firstOrNull { it.time.hour == clockHour }
                ?: hours.lastOrNull { it.time.hour < clockHour }
                ?: hours.firstOrNull()
    }
}
