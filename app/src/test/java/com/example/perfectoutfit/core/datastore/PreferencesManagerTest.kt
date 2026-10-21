package com.example.perfectoutfit.core.datastore

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ThemeMode
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesManagerTest {

    private val dataStore = FakePreferencesDataStore()
    private val preferences = PreferencesManager(dataStore)

    @Test
    fun `defaults to cycling`() = runTest {
        assertEquals(ActivityType.CYCLING, preferences.selectedActivityType.first())
    }

    @Test
    fun `a selection saved before the rename is still selected`() = runTest {
        dataStore.edit { it[stringPreferencesKey("selected_sport")] = "RUNNING" }

        assertEquals(ActivityType.RUNNING, preferences.selectedActivityType.first())
    }

    @Test
    fun `writes only the new key, which wins over the legacy one`() = runTest {
        dataStore.edit { it[stringPreferencesKey("selected_sport")] = "RUNNING" }

        preferences.setSelectedActivityType(ActivityType.CYCLING)

        val prefs = dataStore.data.first()
        assertEquals("CYCLING", prefs[stringPreferencesKey("selected_activity_type")])
        assertEquals("RUNNING", prefs[stringPreferencesKey("selected_sport")])
        assertEquals(ActivityType.CYCLING, preferences.selectedActivityType.first())
    }

    @Test
    fun `an unknown stored value falls back to cycling`() = runTest {
        dataStore.edit { it[stringPreferencesKey("selected_activity_type")] = "SWIMMING" }

        assertEquals(ActivityType.CYCLING, preferences.selectedActivityType.first())
    }

    @Test
    fun `theme defaults to system and is stored`() = runTest {
        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())

        preferences.setThemeMode(ThemeMode.DARK)

        assertEquals(ThemeMode.DARK, PreferencesManager(dataStore).themeMode.first())
    }

    @Test
    fun `an unknown stored theme falls back to system`() = runTest {
        dataStore.edit { it[stringPreferencesKey("theme_mode")] = "SEPIA" }

        assertEquals(ThemeMode.SYSTEM, preferences.themeMode.first())
    }
}
