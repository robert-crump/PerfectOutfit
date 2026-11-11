package com.example.perfectoutfit.feature.home

import com.example.perfectoutfit.core.model.ActivityType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

data class LiveOutfitPayload(
    val selectedHourTime: LocalDateTime?,
    val activityType: ActivityType,
    val workoutDurationHours: Int,
    val prefillItemIds: List<Long>
)

/**
 * Home and the live log wizard hand off through this: Home sets the [LiveOutfitPayload] the
 * wizard starts from, and the wizard reports back once it has saved the outfit.
 */
@Singleton
class LiveOutfitHandoffStore @Inject constructor() {
    private var payload: LiveOutfitPayload? = null

    private val _outfitSaved = MutableStateFlow(false)

    /** The wizard saved a live outfit that Home has not acknowledged yet ([takeSaved]). */
    val outfitSaved: StateFlow<Boolean> = _outfitSaved.asStateFlow()

    fun reportSaved() { _outfitSaved.value = true }

    /** Acknowledges a reported save; true if there was one. */
    fun takeSaved(): Boolean = _outfitSaved.value.also { _outfitSaved.value = false }

    fun set(payload: LiveOutfitPayload) { this.payload = payload }

    /** Returns and clears the pending payload, or null if none was set. */
    fun take(): LiveOutfitPayload? = payload.also { payload = null }
}
