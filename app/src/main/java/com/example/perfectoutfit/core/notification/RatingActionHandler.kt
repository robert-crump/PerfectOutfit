package com.example.perfectoutfit.core.notification

import android.content.Context
import android.widget.Toast
import com.example.perfectoutfit.R
import com.example.perfectoutfit.feature.outfit.OutfitLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingActionHandler @Inject constructor(
    private val outfitLogging: OutfitLogging
) {
    suspend fun handle(context: Context, entryId: Long, rating: Int) {
        outfitLogging.rate(entryId, rating)
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.rating_saved_toast, ratingLabel(context, rating)),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun ratingLabel(context: Context, rating: Int): String = when (rating) {
        -1 -> context.getString(R.string.rating_too_cold)
        0 -> context.getString(R.string.rating_perfect)
        else -> context.getString(R.string.rating_too_hot)
    }
}
