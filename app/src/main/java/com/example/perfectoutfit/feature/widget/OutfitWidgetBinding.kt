package com.example.perfectoutfit.feature.widget

import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ThemeMode
import com.example.perfectoutfit.core.model.WeatherReading
import com.example.perfectoutfit.core.model.referenceTemp
import com.example.perfectoutfit.feature.recommendation.Recommendations
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.roundToInt

/** Everything one activity type's tile shows, decided without Android types. */
data class OutfitWidgetBinding(
    val activityType: ActivityType,
    /** The rounded reference temperature ("12°"), or "–°" when there is none. */
    val valueText: String,
    /** "feels" / "actual", or the hint to open the app when no place is saved. */
    val captionText: String,
    /** True when a recommendation exists for the current hour: the icon then sits on its chip. */
    val lit: Boolean,
    /** False when no place is saved: a tap then opens Home's first step. */
    val opensActivityType: Boolean,
    /** Light or dark colours, as chosen in Settings. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    /** What TalkBack reads for the whole tile. */
    val contentDescription: String get() = "${activityType.displayName}, $valueText $captionText"
}

const val NO_TEMPERATURE_TEXT = "–°"
const val NO_LOCATION_TEXT = "open app"
const val APPARENT_CAPTION = "feels"
const val REAL_CAPTION = "actual"

/**
 * [activityType]'s tile. [placeName] is null when no place is saved; [hour] is null when the
 * saved hours don't cover now. [recommended] is whether a recommendation exists at [hour]'s
 * reference temperature.
 */
fun bindOutfitWidget(
    activityType: ActivityType,
    placeName: String?,
    hour: WeatherReading?,
    useApparent: Boolean,
    recommended: Boolean
): OutfitWidgetBinding {
    if (placeName == null) {
        return OutfitWidgetBinding(
            activityType, NO_TEMPERATURE_TEXT, NO_LOCATION_TEXT, lit = false, opensActivityType = false
        )
    }
    return OutfitWidgetBinding(
        activityType = activityType,
        valueText = hour?.let { "${it.referenceTemp(useApparent).roundToInt()}°" } ?: NO_TEMPERATURE_TEXT,
        captionText = if (useApparent) APPARENT_CAPTION else REAL_CAPTION,
        lit = hour != null && recommended,
        opensActivityType = true
    )
}

/** Loads the saved forecast, the preference and the recommendation, and binds them. */
class OutfitWidgetContent @Inject constructor(
    private val store: WidgetForecastStore,
    private val preferences: PreferencesManager,
    private val recommendations: Recommendations,
    private val clock: Clock
) {
    suspend fun load(activityType: ActivityType): OutfitWidgetBinding {
        val forecast = store.load()
        val useApparent = preferences.useApparentTemperature.first()
        val hour = forecast?.hourAt(LocalDateTime.now(clock))
        val recommended = hour != null &&
            recommendations.find(activityType, hour.referenceTemp(useApparent), useApparent) != null
        return bindOutfitWidget(activityType, forecast?.location?.name, hour, useApparent, recommended)
            .copy(themeMode = preferences.themeMode.first())
    }
}
