package com.example.perfectoutfit.feature.rate

import androidx.lifecycle.SavedStateHandle
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.network.GeocodingApi
import com.example.perfectoutfit.core.network.GeocodingResponse
import com.example.perfectoutfit.feature.catalog.CatalogRepository
import com.example.perfectoutfit.feature.forecast.Forecast
import com.example.perfectoutfit.feature.home.LiveOutfitHandoffStore
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakePreferencesDataStore
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import com.example.perfectoutfit.testutil.outfitLoggingOver
import java.time.Clock

/** A [LogOutfitViewModel] over fakes; the [forecast] and its [clock] come from the test. */
internal fun logOutfitViewModel(
    forecast: Forecast,
    clock: Clock,
    isLive: Boolean = false,
    entryDao: FakeOutfitEntryDao = FakeOutfitEntryDao(),
    snapshotDao: FakeWeatherSnapshotDao = FakeWeatherSnapshotDao(),
    handoff: LiveOutfitHandoffStore = LiveOutfitHandoffStore(),
    geocodingApi: GeocodingApi = object : GeocodingApi {
        override suspend fun search(query: String, count: Int) = GeocodingResponse(emptyList())
    }
) = LogOutfitViewModel(
    savedStateHandle = SavedStateHandle(mapOf("isLive" to isLive)),
    outfitLogging = outfitLoggingOver(entryDao = entryDao, snapshotDao = snapshotDao),
    recommendations = Recommendations(entryDao, FakeClothingItemDao()),
    catalogRepository = CatalogRepository(FakeClothingItemDao(), entryDao, FakeDatabaseTransactionRunner()),
    forecast = forecast,
    liveOutfitHandoffStore = handoff,
    // Stored preferences are the defaults (activity type CYCLING).
    preferencesManager = PreferencesManager(FakePreferencesDataStore()),
    geocodingApi = geocodingApi,
    clock = clock
)
