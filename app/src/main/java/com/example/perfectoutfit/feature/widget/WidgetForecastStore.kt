package com.example.perfectoutfit.feature.widget

import com.example.perfectoutfit.core.model.Place

import androidx.datastore.core.DataStore
import com.example.perfectoutfit.core.time.currentHour
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.perfectoutfit.feature.home.HourlyWeather
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** The DataStore that holds only the widget forecast, kept apart from the settings file. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetForecastData

/**
 * The last place the app resolved, and its hours, as the widget reads them (see "Widget" in
 * CONTEXT.md). Serialized as is: the store keeps no mirror of the forecast hour.
 */
@Serializable
data class WidgetForecast(
    val location: Place,
    val hours: List<HourlyWeather>,
    val fetchedAtMs: Long
) {
    /** The hour of [clock]'s current clock hour, or null when the saved hours don't cover it. */
    fun hourAt(clock: Clock): HourlyWeather? {
        val currentHour = clock.currentHour()
        return hours.firstOrNull { it.time == currentHour }
    }
}

/**
 * Widget forecast: a disposable cache outside Room and outside the export/import schema.
 * Saving keeps only the hours from the current clock hour on, so the file stays small. A cache
 * that can't be read (an older layout, say) counts as nothing saved; the next refresh replaces it.
 */
@Singleton
class WidgetForecastStore @Inject constructor(
    @param:WidgetForecastData private val dataStore: DataStore<Preferences>,
    private val json: Json,
    private val clock: Clock
) {
    /** Saves [location] and its [hours], replacing whatever was saved. */
    suspend fun save(location: Place, hours: List<HourlyWeather>) {
        dataStore.edit { it[KEY] = encode(location, hours) }
    }

    /**
     * Saves [hours] only if [location] is still the saved place, so a background refetch never
     * overwrites a newer place that Home saved meanwhile.
     */
    suspend fun saveIfStillAt(location: Place, hours: List<HourlyWeather>) {
        dataStore.edit { prefs ->
            if (prefs[KEY]?.let(::decode)?.location == location) prefs[KEY] = encode(location, hours)
        }
    }

    /** The saved forecast, or null when none was saved (or it can't be read). */
    suspend fun load(): WidgetForecast? = dataStore.data.first()[KEY]?.let(::decode)

    private fun encode(location: Place, hours: List<HourlyWeather>): String {
        val currentHour = clock.currentHour()
        val forecast = WidgetForecast(
            location = location,
            hours = hours.filter { !it.time.isBefore(currentHour) },
            fetchedAtMs = clock.millis()
        )
        return json.encodeToString(WidgetForecast.serializer(), forecast)
    }

    // SerializationException is an IllegalArgumentException, as is malformed JSON.
    private fun decode(raw: String): WidgetForecast? = try {
        json.decodeFromString(WidgetForecast.serializer(), raw)
    } catch (e: IllegalArgumentException) {
        null
    }

    internal companion object {
        val KEY = stringPreferencesKey("widget_forecast")
    }
}
