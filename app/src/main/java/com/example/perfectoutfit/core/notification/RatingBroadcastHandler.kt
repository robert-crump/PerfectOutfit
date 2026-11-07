package com.example.perfectoutfit.core.notification

import android.content.Context
import android.content.Intent
import com.example.perfectoutfit.core.model.ComfortRating
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The logic behind [RateOutfitActionReceiver]: validates the broadcast and runs the rating on
 * [scope]. Kept out of the Hilt-generated receiver so tests can supply the handler and the scope.
 */
class RatingBroadcastHandler(
    private val rate: suspend (Context, Long, ComfortRating) -> Unit,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    /** [beginAsyncWork] is called only for a valid broadcast; the function it returns when the work ends. */
    fun onReceive(context: Context, intent: Intent, beginAsyncWork: () -> (() -> Unit)) {
        if (intent.action != RateOutfitActionReceiver.ACTION_RATE) return
        val entryId = intent.getLongExtra(RateOutfitActionReceiver.EXTRA_ENTRY_ID, -1L)
        val rating = intent.getIntExtra(RateOutfitActionReceiver.EXTRA_RATING, Int.MIN_VALUE)
        val request = RatingActionValidator.validate(entryId, rating) ?: return

        val appContext = context.applicationContext
        val finishAsyncWork = beginAsyncWork()
        scope.launch {
            try {
                rate(appContext, request.entryId, request.rating)
            } finally {
                finishAsyncWork()
            }
        }
    }
}
