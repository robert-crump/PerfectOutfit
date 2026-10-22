package com.example.perfectoutfit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
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
 * The 3×1 widget (see "Widget" in CONTEXT.md). Thin shell: [OutfitWidgetContent] decides what
 * it shows, [outfitWidgetViews] turns that into RemoteViews.
 */
@AndroidEntryPoint
class OutfitWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var content: OutfitWidgetContent
    @Inject lateinit var scheduler: WidgetForecastScheduler

    override fun onEnabled(context: Context) {
        scheduler.schedule()
    }

    override fun onDisabled(context: Context) {
        scheduler.cancel()
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        redraw(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        redraw(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    /** Loads the content once and applies it to [ids]; keeps the broadcast alive while it loads. */
    private fun redraw(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        scope.launch {
            try {
                val binding = content.load()
                for (id in ids) {
                    manager.updateAppWidget(id, outfitWidgetViews(appContext, binding, manager.getAppWidgetOptions(id)))
                }
            } catch (e: Exception) {
                // The widget keeps showing its last content; the next redraw tries again.
                Log.e(TAG, "Redrawing the widget failed", e)
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

/** Below this height (dp) basis and place share one line. */
private const val TALL_MIN_HEIGHT_DP = 80

/**
 * The widget's RemoteViews: the stacked layout on tall widgets, the one-line layout on short
 * ones. Android 12+ picks by size itself; older launchers report the size in [options].
 */
fun outfitWidgetViews(context: Context, binding: OutfitWidgetBinding, options: Bundle): RemoteViews {
    val tall = views(context, binding, tall = true)
    val short = views(context, binding, tall = false)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        return RemoteViews(
            mapOf(
                SizeF(0f, 0f) to short,
                SizeF(0f, TALL_MIN_HEIGHT_DP.toFloat()) to tall
            )
        )
    }
    // Portrait height is the max height option; most launchers are used in portrait.
    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
    return if (height >= TALL_MIN_HEIGHT_DP) tall else short
}

/** Each icon twice: on its chip (lit) and faint without one (unlit); only one is visible. */
private val LIT_ICON_IDS = intArrayOf(R.id.widget_icon_lit_0, R.id.widget_icon_lit_1)
private val UNLIT_ICON_IDS = intArrayOf(R.id.widget_icon_unlit_0, R.id.widget_icon_unlit_1)

/** Each icon's 30 % of the widget; the whole area is the tap target. */
private val ICON_AREA_IDS = intArrayOf(R.id.widget_icon_area_0, R.id.widget_icon_area_1)

private fun views(context: Context, binding: OutfitWidgetBinding, tall: Boolean): RemoteViews {
    val layout = if (tall) R.layout.outfit_widget else R.layout.outfit_widget_short
    val views = RemoteViews(context.packageName, layout)
    views.setTextViewText(R.id.widget_temperature, binding.valueText)
    if (tall) {
        views.setTextViewText(R.id.widget_basis, binding.basisText.orEmpty())
        views.setViewVisibility(R.id.widget_basis, if (binding.basisText == null) View.GONE else View.VISIBLE)
        views.setTextViewText(R.id.widget_place, binding.placeText)
    } else {
        views.setTextViewText(R.id.widget_place, binding.combinedLine)
    }
    binding.icons.forEachIndexed { index, icon ->
        val lit = LIT_ICON_IDS[index]
        val unlit = UNLIT_ICON_IDS[index]
        views.setImageViewResource(lit, icon.activityType.iconRes)
        views.setImageViewResource(unlit, icon.activityType.iconRes)
        views.setViewVisibility(lit, if (icon.lit) View.VISIBLE else View.GONE)
        views.setViewVisibility(unlit, if (icon.lit) View.GONE else View.VISIBLE)
        views.setContentDescription(ICON_AREA_IDS[index], icon.activityType.displayName)
        val target = if (binding.iconsOpenActivityType) icon.activityType else null
        views.setOnClickPendingIntent(ICON_AREA_IDS[index], openHomeIntent(context, target, requestCode = index + 1))
    }
    views.setOnClickPendingIntent(R.id.widget_temperature_area, openHomeIntent(context, null, requestCode = 0))
    when (binding.themeMode) {
        ThemeMode.SYSTEM -> Unit
        ThemeMode.LIGHT -> forcePalette(context, views, WidgetPalette.LIGHT, tall)
        ThemeMode.DARK -> forcePalette(context, views, WidgetPalette.DARK, tall)
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
private fun forcePalette(context: Context, views: RemoteViews, palette: WidgetPalette, tall: Boolean) {
    views.setInt(android.R.id.background, "setBackgroundResource", palette.background)
    views.setTextColor(R.id.widget_temperature, context.getColor(palette.onSurface))
    val variant = context.getColor(palette.onSurfaceVariant)
    views.setTextColor(R.id.widget_place, variant)
    if (tall) views.setTextColor(R.id.widget_basis, variant)
    // A colour filter wins over the layout's android:tint.
    for (id in LIT_ICON_IDS) {
        views.setInt(id, "setBackgroundResource", palette.chip)
        views.setInt(id, "setColorFilter", context.getColor(palette.onChip))
    }
    for (id in UNLIT_ICON_IDS) views.setInt(id, "setColorFilter", variant)
}

/** Opens Home: on [activityType]'s result step, or on its first step when null. */
private fun openHomeIntent(context: Context, activityType: ActivityType?, requestCode: Int): PendingIntent {
    val intent = Intent(context, MainActivity::class.java)
        .setAction(MainActivity.ACTION_OPEN_HOME)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    if (activityType != null) intent.putExtra(MainActivity.EXTRA_ACTIVITY_TYPE, activityType.name)
    return PendingIntent.getActivity(
        context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
