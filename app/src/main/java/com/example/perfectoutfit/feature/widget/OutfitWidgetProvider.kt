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
import androidx.annotation.IdRes
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
 * A home-screen widget (see "Widget" in CONTEXT.md). Thin shell: [OutfitWidgetContent] decides
 * what it shows, [views] turns that into RemoteViews. Every provider shares the forecast refresh.
 */
abstract class OutfitWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var content: OutfitWidgetContent
    @Inject lateinit var scheduler: WidgetForecastScheduler

    /** Names this provider in logs. */
    protected abstract val logName: String

    /** What each of this provider's widgets shows now. */
    protected abstract suspend fun views(context: Context): RemoteViews

    override fun onEnabled(context: Context) {
        scheduler.schedule()
    }

    /** This provider's last widget is gone; the refresh stops only when no widget is left at all. */
    override fun onDisabled(context: Context) {
        if (widgetProviders.all { placedWidgetIds(context, it).isEmpty() }) scheduler.cancel()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                appWidgetManager.updateAppWidget(appWidgetIds, views(appContext))
            } catch (e: Exception) {
                // The widget keeps showing its last content; the next redraw tries again.
                Log.e(TAG, "Redrawing the $logName widget failed", e)
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

/**
 * One activity type's 1×1 tile. Each activity type has its own subclass, so the picker offers
 * one tile per activity type.
 */
abstract class ActivityWidgetProvider(private val activityType: ActivityType) : OutfitWidgetProvider() {
    override val logName: String get() = activityType.displayName

    override suspend fun views(context: Context): RemoteViews =
        outfitWidgetViews(context, content.load(activityType))
}

@AndroidEntryPoint
class CyclingWidgetProvider : ActivityWidgetProvider(ActivityType.CYCLING)

@AndroidEntryPoint
class RunningWidgetProvider : ActivityWidgetProvider(ActivityType.RUNNING)

@AndroidEntryPoint
class BikeCommuteWidgetProvider : ActivityWidgetProvider(ActivityType.BIKE_COMMUTE)

@AndroidEntryPoint
class WalkingWidgetProvider : ActivityWidgetProvider(ActivityType.WALKING)

/** The 2×2 grid: every activity type's tile, without the text (issue #30). */
@AndroidEntryPoint
class GridWidgetProvider : OutfitWidgetProvider() {
    override val logName: String get() = "grid"

    override suspend fun views(context: Context): RemoteViews = gridWidgetViews(context, content.loadAll())
}

/** The provider that draws [activityType]'s 1×1 tiles. */
fun widgetProviderOf(activityType: ActivityType): Class<out ActivityWidgetProvider> = when (activityType) {
    ActivityType.CYCLING -> CyclingWidgetProvider::class.java
    ActivityType.RUNNING -> RunningWidgetProvider::class.java
    ActivityType.BIKE_COMMUTE -> BikeCommuteWidgetProvider::class.java
    ActivityType.WALKING -> WalkingWidgetProvider::class.java
}

/** Every provider: one per activity type, then the grid. */
val widgetProviders: List<Class<out OutfitWidgetProvider>>
    get() = ActivityType.entries.map(::widgetProviderOf) + GridWidgetProvider::class.java

/** The ids of [provider]'s widgets on the home screen. */
fun placedWidgetIds(context: Context, provider: Class<out OutfitWidgetProvider>): IntArray =
    AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, provider))

/** The 1×1 tile's RemoteViews: the icon, lit on its chip or faint, over the temperature and its caption. */
fun outfitWidgetViews(context: Context, binding: OutfitWidgetBinding): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.outfit_widget)
    bindTile(context, views, android.R.id.background, binding, binding.contentDescription)
    views.setTextViewText(R.id.widget_temperature, binding.valueText)
    views.setTextViewText(R.id.widget_caption, binding.captionText)
    forcedPalette(binding.themeMode)?.let { palette ->
        views.setTextColor(R.id.widget_temperature, context.getColor(palette.onSurface))
        views.setTextColor(R.id.widget_caption, context.getColor(palette.onSurfaceVariant))
    }
    return views
}

/** The grid's RemoteViews: each binding's tile, added into its activity type's slot. */
fun gridWidgetViews(context: Context, bindings: List<OutfitWidgetBinding>): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.outfit_widget_grid)
    for (binding in bindings) {
        val tile = RemoteViews(context.packageName, R.layout.outfit_widget_grid_tile)
        bindTile(context, tile, R.id.widget_tile, binding, binding.gridContentDescription)
        views.addView(gridSlotOf(binding.activityType), tile)
    }
    return views
}

@IdRes
private fun gridSlotOf(activityType: ActivityType): Int = when (activityType) {
    ActivityType.CYCLING -> R.id.widget_grid_cycling
    ActivityType.RUNNING -> R.id.widget_grid_running
    ActivityType.BIKE_COMMUTE -> R.id.widget_grid_bike_commute
    ActivityType.WALKING -> R.id.widget_grid_walking
}

/**
 * The part both widgets share: [tileId]'s background and tap target, and the icon on it
 * (outfit_widget_icon), lit on its chip or faint.
 */
private fun bindTile(
    context: Context,
    views: RemoteViews,
    @IdRes tileId: Int,
    binding: OutfitWidgetBinding,
    contentDescription: String
) {
    views.setImageViewResource(R.id.widget_icon_lit, binding.activityType.iconRes)
    views.setImageViewResource(R.id.widget_icon_unlit, binding.activityType.iconRes)
    views.setViewVisibility(R.id.widget_icon_lit, if (binding.lit) View.VISIBLE else View.GONE)
    views.setViewVisibility(R.id.widget_icon_unlit, if (binding.lit) View.GONE else View.VISIBLE)
    views.setContentDescription(tileId, contentDescription)
    val target = if (binding.opensActivityType) binding.activityType else null
    views.setOnClickPendingIntent(tileId, openHomeIntent(context, target))
    forcedPalette(binding.themeMode)?.let { palette ->
        views.setInt(tileId, "setBackgroundResource", palette.background)
        // A colour filter wins over the layout's android:tint.
        views.setInt(R.id.widget_icon_lit, "setBackgroundResource", palette.chip)
        views.setInt(R.id.widget_icon_lit, "setColorFilter", context.getColor(palette.onChip))
        views.setInt(R.id.widget_icon_unlit, "setColorFilter", context.getColor(palette.onSurfaceVariant))
    }
}

/**
 * The palette that overrides the layout's colours, which the launcher resolves for the device's
 * mode; null for System. Colours are resolved by the caller and passed as values; only the
 * drawables go as resources.
 */
private fun forcedPalette(themeMode: ThemeMode): WidgetPalette? = when (themeMode) {
    ThemeMode.SYSTEM -> null
    ThemeMode.LIGHT -> WidgetPalette.LIGHT
    ThemeMode.DARK -> WidgetPalette.DARK
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
