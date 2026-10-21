package com.example.perfectoutfit.feature.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.perfectoutfit.feature.forecast.ForecastLocation
import com.example.perfectoutfit.feature.home.HourlyWeather
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** The DataStore that holds only the widget forecast, kept apart from the settings file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetForecastData

/** The last place the app resolved, and its hours, as the widget reads them (see "Widget" in CONTEXT.md). */
data class WidgetForecast(
    val location: ForecastLocation,
    val hours: List<HourlyWeather>,
    val fetchedAtMs: Long
) {
    /** The hour of [now], or null when the saved hours don't cover it. */
    fun hourAt(now: LocalDateTime): HourlyWeather? {
        val clockHour = now.truncatedTo(ChronoUnit.HOURS)
        return hours.firstOrNull { it.time == clockHour }
    }
}

/**
 * Widget forecast: a disposable cache outside Room and outside the export/import schema.
 * Saving keeps only the hours from the current clock hour on, so the file stays small.
 */
@Singleton
class WidgetForecastStore @Inject constructor(
    @param:WidgetForecastData private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val clock: Clock
) {
    /** Saves [location] and its [hours], replacing whatever was saved. */
    suspend fun save(location: ForecastLocation, hours: List<HourlyWeather>) {
        dataStore.edit { it[KEY] = encode(location, hours) }
    }

    /**
     * Saves [hours] only if [location] is still the saved place, so a background refetch never
     * overwrites a newer place that Home saved meanwhile.
     */
    suspend fun saveIfStillAt(location: ForecastLocation, hours: List<HourlyWeather>) {
        dataStore.edit { prefs ->
            if (prefs[KEY]?.let(::decode)?.location == location) prefs[KEY] = encode(location, hours)
        }
    }

    /** The saved forecast, or null when none was saved (or it can't be read). */
    suspend fun load(): WidgetForecast? = dataStore.data.first()[KEY]?.let(::decode)

    private fun encode(location: ForecastLocation, hours: List<HourlyWeather>): String {
        val currentHour = LocalDateTime.now(clock).truncatedTo(ChronoUnit.HOURS)
        val stored = StoredForecast(
            lat = location.lat,
            lon = location.lon,
            name = location.name,
            hours = hours.filter { !it.time.isBefore(currentHour) }.map(StoredHour::from),
            fetchedAtMs = clock.millis()
        )
        return json.encodeToString(StoredForecast.serializer(), stored)
    }

    private fun decode(raw: String): WidgetForecast? {
        val stored = try {
            json.decodeFromString(StoredForecast.serializer(), raw)
        } catch (e: SerializationException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null
        }
        return try {
            WidgetForecast(
                location = ForecastLocation(stored.lat, stored.lon, stored.name),
                hours = stored.hours.map(StoredHour::toHourlyWeather),
                fetchedAtMs = stored.fetchedAtMs
            )
        } catch (e: DateTimeParseException) {
            null
        }
    }

    @Serializable
    private data class StoredForecast(
        val lat: Double,
        val lon: Double,
        val name: String,
        val hours: List<StoredHour>,
        val fetchedAtMs: Long
    )

    @Serializable
    private data class StoredHour(
        val time: String,
        val temperatureCelsius: Double,
        val apparentTemperatureCelsius: Double,
        val windSpeedKmh: Double,
        val windDirectionDegrees: Int,
        val uvIndex: Int,
        val cloudCoverPercent: Int,
        val precipitationProbabilityPercent: Int
    ) {
        fun toHourlyWeather() = HourlyWeather(
            time = LocalDateTime.parse(time),
            temperatureCelsius = temperatureCelsius,
            apparentTemperatureCelsius = apparentTemperatureCelsius,
            windSpeedKmh = windSpeedKmh,
            windDirectionDegrees = windDirectionDegrees,
            uvIndex = uvIndex,
            cloudCoverPercent = cloudCoverPercent,
            precipitationProbabilityPercent = precipitationProbabilityPercent
        )

        companion object {
            fun from(hour: HourlyWeather) = StoredHour(
                time = hour.time.toString(),
                temperatureCelsius = hour.temperatureCelsius,
                apparentTemperatureCelsius = hour.apparentTemperatureCelsius,
                windSpeedKmh = hour.windSpeedKmh,
                windDirectionDegrees = hour.windDirectionDegrees,
                uvIndex = hour.uvIndex,
                cloudCoverPercent = hour.cloudCoverPercent,
                precipitationProbabilityPercent = hour.precipitationProbabilityPercent
            )
        }
    }

    private companion object {
        val KEY = stringPreferencesKey("widget_forecast")
    }
}
