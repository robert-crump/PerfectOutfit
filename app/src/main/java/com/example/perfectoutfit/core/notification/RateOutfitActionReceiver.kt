package com.example.perfectoutfit.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Thin Hilt glue; the behavior lives in [RatingBroadcastHandler]. */
@AndroidEntryPoint
class RateOutfitActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var ratingActionHandler: RatingActionHandler

    override fun onReceive(context: Context, intent: Intent) {
        RatingBroadcastHandler(ratingActionHandler::handle).onReceive(context, intent) {
            val pendingResult = goAsync()
            return@onReceive { pendingResult.finish() }
        }
    }

    companion object {
        const val ACTION_RATE = "com.example.perfectoutfit.action.RATE_OUTFIT"
        const val EXTRA_ENTRY_ID = "com.example.perfectoutfit.extra.ENTRY_ID"
        const val EXTRA_RATING = "com.example.perfectoutfit.extra.RATING"
    }
}
