package com.example.perfectoutfit.core.notification

import com.example.perfectoutfit.core.model.ActivityType

/** Recording fake for [RatingReminder]; lets JVM unit tests assert on show/cancel calls. */
class FakeRatingReminder : RatingReminder {

    data class ShowCall(
        val outfitEntryId: Long,
        val activityType: ActivityType,
        val dateMs: Long,
        val durationHours: Int
    )

    val showCalls = mutableListOf<ShowCall>()
    val cancelledEntryIds = mutableListOf<Long>()

    override fun show(outfitEntryId: Long, activityType: ActivityType, dateMs: Long, durationHours: Int) {
        showCalls.add(ShowCall(outfitEntryId, activityType, dateMs, durationHours))
    }

    override fun cancel(outfitEntryId: Long) {
        cancelledEntryIds.add(outfitEntryId)
    }
}
