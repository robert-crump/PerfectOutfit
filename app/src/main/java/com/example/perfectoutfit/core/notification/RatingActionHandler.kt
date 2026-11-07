package com.example.perfectoutfit.core.notification

import android.content.Context
import android.widget.Toast
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.ComfortRating
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
    suspend fun handle(context: Context, entryId: Long, rating: ComfortRating) {
        val message = when (outfitLogging.rate(entryId, rating)) {
            RateResult.SAVED -> context.getString(R.string.rating_saved_toast, context.getString(rating.labelRes))
            RateResult.ENTRY_MISSING -> context.getString(R.string.rating_entry_missing_toast)
        }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
