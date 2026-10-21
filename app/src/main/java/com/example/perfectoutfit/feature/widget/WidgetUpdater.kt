package com.example.perfectoutfit.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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

/** Asks [OutfitWidgetProvider] to redraw its widgets; a no-op when none is placed. */
@Singleton
class AndroidWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context
) : WidgetUpdater {

    override fun redraw() {
        val component = ComponentName(context, OutfitWidgetProvider::class.java)
        val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(component)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }
}
