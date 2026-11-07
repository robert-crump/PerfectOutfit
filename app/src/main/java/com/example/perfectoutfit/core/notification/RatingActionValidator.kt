package com.example.perfectoutfit.core.notification

import com.example.perfectoutfit.core.model.ComfortRating

object RatingActionValidator {
    data class RatingRequest(val entryId: Long, val rating: ComfortRating)

    fun validate(entryId: Long, rating: Int): RatingRequest? {
        if (entryId <= 0L) return null
        val comfortRating = ComfortRating.fromValue(rating) ?: return null
        return RatingRequest(entryId, comfortRating)
    }
}
