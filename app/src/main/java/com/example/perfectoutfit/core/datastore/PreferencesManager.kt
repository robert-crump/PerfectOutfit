package com.example.perfectoutfit.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val SELECTED_ACTIVITY_TYPE = stringPreferencesKey("selected_activity_type")

        /** Written by versions before the activity-type rename; read only as a fallback. */
        private val LEGACY_SELECTED_SPORT = stringPreferencesKey("selected_sport")
        private val USE_APPARENT_TEMPERATURE = booleanPreferencesKey("use_apparent_temperature")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val selectedActivityType: Flow<ActivityType> = dataStore.data.map { prefs ->
        val stored = prefs[SELECTED_ACTIVITY_TYPE] ?: prefs[LEGACY_SELECTED_SPORT]
        ActivityType.entries.find { it.name == stored } ?: ActivityType.CYCLING
    }

    val useApparentTemperature: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[USE_APPARENT_TEMPERATURE] ?: true
    }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.find { it.name == prefs[THEME_MODE] } ?: ThemeMode.SYSTEM
    }

    suspend fun setSelectedActivityType(activityType: ActivityType) {
        dataStore.edit { prefs ->
            prefs[SELECTED_ACTIVITY_TYPE] = activityType.name
        }
    }

    suspend fun setUseApparentTemperature(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[USE_APPARENT_TEMPERATURE] = value
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[THEME_MODE] = mode.name
        }
    }
}
