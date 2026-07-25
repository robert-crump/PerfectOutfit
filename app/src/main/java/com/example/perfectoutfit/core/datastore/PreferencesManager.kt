package com.example.perfectoutfit.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.perfectoutfit.core.model.Sport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val SELECTED_SPORT = stringPreferencesKey("selected_sport")
        private val USE_APPARENT_TEMPERATURE = booleanPreferencesKey("use_apparent_temperature")
    }

    val selectedSport: Flow<Sport> = dataStore.data.map { prefs ->
        prefs[SELECTED_SPORT]?.let { Sport.valueOf(it) } ?: Sport.CYCLING
    }

    val useApparentTemperature: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[USE_APPARENT_TEMPERATURE] ?: true
    }

    suspend fun setSelectedSport(sport: Sport) {
        dataStore.edit { prefs ->
            prefs[SELECTED_SPORT] = sport.name
        }
    }

    suspend fun setUseApparentTemperature(value: Boolean) {
        dataStore.edit { prefs ->
            prefs[USE_APPARENT_TEMPERATURE] = value
        }
    }
}
