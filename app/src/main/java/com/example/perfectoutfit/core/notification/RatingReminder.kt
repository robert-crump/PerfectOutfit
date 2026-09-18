package com.example.perfectoutfit.core.notification

import com.example.perfectoutfit.core.model.Sport

/**
 * Rating reminder: prompts the user to rate an outfit entry after a workout.
 * Two adapters exist: [AndroidRatingReminder] (system notifications) and a recording fake
 * under `app/src/test` for JVM unit tests.
 */
interface RatingReminder {
    fun show(outfitEntryId: Long, sport: Sport, dateMs: Long, durationHours: Int)
    fun cancel(outfitEntryId: Long)
}
