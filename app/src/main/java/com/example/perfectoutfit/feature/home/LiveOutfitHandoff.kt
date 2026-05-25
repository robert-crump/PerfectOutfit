package com.example.perfectoutfit.feature.home

import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class LiveOutfitPayload(
    val allHours: List<HourlyWeather>,
    val lat: Double,
    val lon: Double,
    val locationName: String,
    val selectedHourTime: LocalDateTime?,
    val workoutDurationHours: Int,
    val prefillItemIds: List<Long>
)

@Singleton
class LiveOutfitHandoffStore @Inject constructor() {
    private var payload: LiveOutfitPayload? = null

    fun set(payload: LiveOutfitPayload) { this.payload = payload }

    /** Returns and clears the pending payload, or null if none was set. */
    fun take(): LiveOutfitPayload? = payload.also { payload = null }
}
