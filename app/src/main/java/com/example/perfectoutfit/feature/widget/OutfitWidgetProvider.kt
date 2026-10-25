package com.example.perfectoutfit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.example.perfectoutfit.MainActivity
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One activity type's 1×1 tile (see "Widget" in CONTEXT.md). Thin shell: [OutfitWidgetContent]
 * decides what it shows, [outfitWidgetViews] turns that into RemoteViews. Each activity type has
 * its own subclass, so the picker offers one tile per activity type.
 */
abstract class OutfitWidgetProvider(private val activityType: ActivityType) : AppWidgetProvider() {

    @Inject lateinit var content: OutfitWidgetContent
    @Inject lateinit var scheduler: WidgetForecastScheduler

    override fun onEnabled(context: Context) {
        scheduler.schedule()
    }

    /** This activity type's last tile is gone; the refresh stops only when no tile is left at all. */
    override fun onDisabled(context: Context) {
        if (ActivityType.entries.all { placedWidgetIds(context, it).isEmpty() }) scheduler.cancel()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val views = outfitWidgetViews(appContext, content.load(activityType))
                appWidgetManager.updateAppWidget(appWidgetIds, views)
            } catch (e: Exception) {
                // The tile keeps showing its last content; the next redraw tries again.
                Log.e(TAG, "Redrawing the ${activityType.displayName} widget failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "OutfitWidget"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

@AndroidEntryPoint
class CyclingWidgetProvider : OutfitWidgetProvider(ActivityType.CYCLING)

@AndroidEntryPoint
class RunningWidgetProvider : OutfitWidgetProvider(ActivityType.RUNNING)

@AndroidEntryPoint
class BikeCommuteWidgetProvider : OutfitWidgetProvider(ActivityType.BIKE_COMMUTE)

@AndroidEntryPoint
class WalkingWidgetProvider : OutfitWidgetProvider(ActivityType.WALKING)

/** The provider that draws [activityType]'s tiles. */
fun widgetProviderOf(activityType: ActivityType): Class<out OutfitWidgetProvider> = when (activityType) {
    ActivityType.CYCLING -> CyclingWidgetProvider::class.java
    ActivityType.RUNNING -> RunningWidgetProvider::class.java
    ActivityType.BIKE_COMMUTE -> BikeCommuteWidgetProvider::class.java
    ActivityType.WALKING -> WalkingWidgetProvider::class.java
}

/** The ids of [activityType]'s tiles on the home screen. */
fun placedWidgetIds(context: Context, activityType: ActivityType): IntArray =
    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, widgetProviderOf(activityType)))

/** The tile's RemoteViews: the icon, lit on its chip or faint, over the temperature and its caption. */
fun outfitWidgetViews(context: Context, binding: OutfitWidgetBinding): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.outfit_widget)
    views.setImageViewResource(R.id.widget_icon_lit, binding.activityType.iconRes)
    views.setImageViewResource(R.id.widget_icon_unlit, binding.activityType.iconRes)
    views.setViewVisibility(R.id.widget_icon_lit, if (binding.lit) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.widget_icon_unlit, if (binding.lit) View.GONE else View.VISIBLE)
    views.setTextViewText(R.id.widget_temperature, binding.valueText)
    views.setTextViewText(R.id.widget_caption, binding.captionText)
    views.setContentDescription(android.R.id.background, binding.contentDescription)
    val target = if (binding.opensActivityType) binding.activityType else null
    views.setOnClickPendingIntent(android.R.id.background, openHomeIntent(context, target))
    when (binding.themeMode) {
        ThemeMode.SYSTEM -> Unit
        ThemeMode.LIGHT -> forcePalette(context, views, WidgetPalette.LIGHT)
        ThemeMode.DARK -> forcePalette(context, views, WidgetPalette.DARK)
    }
    return views
}

/** One of the widget's two colour sets, as resources. */
private enum class WidgetPalette(
    @param:DrawableRes val background: Int,
    @param:ColorRes val onSurface: Int,
    @param:ColorRes val onSurfaceVariant: Int,
    @param:DrawableRes val chip: Int,
    @param:ColorRes val onChip: Int
) {
    LIGHT(
        R.drawable.outfit_widget_background_light, R.color.widget_on_surface_light,
        R.color.widget_on_surface_variant_light, R.drawable.outfit_widget_chip_light, R.color.widget_on_chip_light
    ),
    DARK(
        R.drawable.outfit_widget_background_dark, R.color.widget_on_surface_dark,
        R.color.widget_on_surface_variant_dark, R.drawable.outfit_widget_chip_dark, R.color.widget_on_chip_dark
    )
}

/**
 * Overrides the layout's colours, which the launcher resolves for the device's mode, with
 * [palette]. Colours are resolved here and passed as values; only the drawables go as resources.
 */
private fun forcePalette(context: Context, views: RemoteViews, palette: WidgetPalette) {
    views.setInt(android.R.id.background, "setBackgroundResource", palette.background)
    views.setTextColor(R.id.widget_temperature, context.getColor(palette.onSurface))
    val variant = context.getColor(palette.onSurfaceVariant)
    views.setTextColor(R.id.widget_caption, variant)
    // A colour filter wins over the layout's android:tint.
    views.setInt(R.id.widget_icon_lit, "setBackgroundResource", palette.chip)
    views.setInt(R.id.widget_icon_lit, "setColorFilter", context.getColor(palette.onChip))
    views.setInt(R.id.widget_icon_unlit, "setColorFilter", variant)
}

/**
 * Opens Home: on [activityType]'s result step, or on its first step when null. Each activity
 * type gets its own request code, so one tile's intent never replaces another's extras.
 */
private fun openHomeIntent(context: Context, activityType: ActivityType?): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    if (activityType != null) intent.putExtra(MainActivity.EXTRA_ACTIVITY_TYPE, activityType.name)
    return PendingIntent.getActivity(
        context, activityType?.let { it.ordinal + 1 } ?: 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
