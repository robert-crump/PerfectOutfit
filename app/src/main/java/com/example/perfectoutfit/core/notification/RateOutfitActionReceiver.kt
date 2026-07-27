package com.example.perfectoutfit.core.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
open class RateOutfitActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var ratingActionHandler: RatingActionHandler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RATE) return
        val entryId = intent.getLongExtra(EXTRA_ENTRY_ID, -1L)
        val rating = intent.getIntExtra(EXTRA_RATING, Int.MIN_VALUE)
        val request = RatingActionValidator.validate(entryId, rating) ?: return

        val appContext = context.applicationContext
        val finishAsyncWork = beginAsyncWork()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ratingActionHandler.handle(appContext, request.entryId, request.rating)
            } finally {
                finishAsyncWork()
            }
        }
    }

    /**
     * Wraps [goAsync] so tests can substitute a no-op completion instead of the real
     * PendingResult, which Robolectric cannot supply outside a genuine async broadcast dispatch.
     */
    internal open fun beginAsyncWork(): () -> Unit {
        val pendingResult = goAsync()
        return { pendingResult.finish() }
    }

    companion object {
        const val ACTION_RATE = "com.example.perfectoutfit.action.RATE_OUTFIT"
        const val EXTRA_ENTRY_ID = "com.example.perfectoutfit.extra.ENTRY_ID"
        const val EXTRA_RATING = "com.example.perfectoutfit.extra.RATING"
    }
}
