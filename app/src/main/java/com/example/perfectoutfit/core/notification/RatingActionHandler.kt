package com.example.perfectoutfit.core.notification

import android.content.Context
import android.widget.Toast
import com.example.perfectoutfit.R
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import com.example.perfectoutfit.feature.outfit.RateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingActionHandler @Inject constructor(
    private val outfitLogging: OutfitLogging
) {
    suspend fun handle(context: Context, entryId: Long, rating: Int) {
        val message = when (outfitLogging.rate(entryId, rating)) {
            RateResult.SAVED -> context.getString(R.string.rating_saved_toast, ratingLabel(context, rating))
            RateResult.ENTRY_MISSING -> context.getString(R.string.rating_entry_missing_toast)
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun ratingLabel(context: Context, rating: Int): String = when (rating) {
        -1 -> context.getString(R.string.rating_too_cold)
        0 -> context.getString(R.string.rating_perfect)
        else -> context.getString(R.string.rating_too_hot)
    }
}
