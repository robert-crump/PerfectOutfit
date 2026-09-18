package com.example.perfectoutfit.core.notification

import com.example.perfectoutfit.core.model.Sport

/** Recording fake for [RatingReminder]; lets JVM unit tests assert on show/cancel calls. */
class FakeRatingReminder : RatingReminder {

    data class ShowCall(
        val outfitEntryId: Long,
        val sport: Sport,
        val dateMs: Long,
        val durationHours: Int
    )

    val showCalls = mutableListOf<ShowCall>()
    val cancelledEntryIds = mutableListOf<Long>()

    override fun show(outfitEntryId: Long, sport: Sport, dateMs: Long, durationHours: Int) {
        showCalls.add(ShowCall(outfitEntryId, sport, dateMs, durationHours))
    }

    override fun cancel(outfitEntryId: Long) {
        cancelledEntryIds.add(outfitEntryId)
    }
}
