package com.example.perfectoutfit.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.perfectoutfit.core.model.ActivityType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Widget updater: redraws every placed widget from the saved forecast, without fetching.
 * Two adapters exist: [AndroidWidgetUpdater] and a recording fake under `app/src/test`.
 */
interface WidgetUpdater {
    fun redraw()
}

/** Asks each activity type's [OutfitWidgetProvider] to redraw its tiles; skips those with none placed. */
@Singleton
class AndroidWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetUpdater {

    override fun redraw() {
        for (activityType in ActivityType.entries) {
            val ids = placedWidgetIds(context, activityType)
            if (ids.isEmpty()) continue
            context.sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(ComponentName(context, widgetProviderOf(activityType)))
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
