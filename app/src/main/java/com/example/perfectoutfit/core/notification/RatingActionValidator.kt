package com.example.perfectoutfit.core.notification

object RatingActionValidator {
    private val VALID_RATINGS = setOf(-1, 0, 1)

    data class RatingRequest(val entryId: Long, val rating: Int)

    fun validate(entryId: Long, rating: Int): RatingRequest? {
        if (entryId <= 0L) return null
        if (rating !in VALID_RATINGS) return null
        return RatingRequest(entryId, rating)
    }
}
