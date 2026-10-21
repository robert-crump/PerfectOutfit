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

/** The activity types the widget offers, in the order it shows them. */
val WIDGET_ACTIVITY_TYPES = listOf(ActivityType.BIKE_COMMUTE, ActivityType.WALKING)

/** One activity icon: lit when a recommendation exists for the current hour. */
data class WidgetActivityIcon(val activityType: ActivityType, val lit: Boolean)

/** Everything the widget shows, decided without Android types. */
data class OutfitWidgetBinding(
    /** The rounded reference temperature ("12°"), or "–°" when there is none. */
    val valueText: String,
    /** "Feels like" / "Real temp."; null when no place is saved. */
    val basisText: String?,
    /** The saved place's name, or the hint to open the app. */
    val placeText: String,
    val icons: List<WidgetActivityIcon>,
    /** False when no place is saved: every tap then opens Home's first step. */
    val iconsOpenActivityType: Boolean,
    /** Light or dark colours, as chosen in Settings. */
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    /** Basis and place on one line, for widgets too short to stack them. */
    val combinedLine: String get() = basisText?.let { "$it · $placeText" } ?: placeText
}

const val NO_TEMPERATURE_TEXT = "–°"
const val NO_LOCATION_TEXT = "Open app to set location"

/**
 * The widget's content. [placeName] is null when no place is saved; [hour] is null when the
 * saved hours don't cover now. [recommended] are the activity types with a recommendation
 * at [hour]'s reference temperature.
 */
fun bindOutfitWidget(
    placeName: String?,
    hour: WeatherReading?,
    useApparent: Boolean,
    recommended: Set<ActivityType>
): OutfitWidgetBinding {
    val icons = WIDGET_ACTIVITY_TYPES.map { WidgetActivityIcon(it, lit = hour != null && it in recommended) }
    if (placeName == null) {
        return OutfitWidgetBinding(NO_TEMPERATURE_TEXT, null, NO_LOCATION_TEXT, icons, iconsOpenActivityType = false)
    }
    return OutfitWidgetBinding(
        valueText = hour?.let { "${it.referenceTemp(useApparent).roundToInt()}°" } ?: NO_TEMPERATURE_TEXT,
        basisText = if (useApparent) "Feels like" else "Real temp.",
        placeText = placeName,
        icons = icons,
        iconsOpenActivityType = true
    )
}

/** Loads the saved forecast, the preference and the recommendations, and binds them. */
class OutfitWidgetContent @Inject constructor(
    private val store: WidgetForecastStore,
    private val preferences: PreferencesManager,
    private val recommendations: Recommendations,
    private val clock: Clock
) {
    suspend fun load(): OutfitWidgetBinding {
        val forecast = store.load()
        val useApparent = preferences.useApparentTemperature.first()
        val hour = forecast?.hourAt(LocalDateTime.now(clock))
        val recommended = if (hour == null) {
            emptySet()
        } else {
            WIDGET_ACTIVITY_TYPES.filter {
                recommendations.find(it, hour.referenceTemp(useApparent), useApparent) != null
            }.toSet()
        }
        return bindOutfitWidget(forecast?.location?.name, hour, useApparent, recommended)
            .copy(themeMode = preferences.themeMode.first())
    }
}
