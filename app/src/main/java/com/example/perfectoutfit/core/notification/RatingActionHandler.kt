package com.example.perfectoutfit.core.notification

import android.content.Context
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import com.example.perfectoutfit.R
import com.example.perfectoutfit.feature.home.OutfitRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RatingActionHandler @Inject constructor(
    private val outfitRepository: OutfitRepository
) {
    var mainDispatcher: CoroutineDispatcher = Dispatchers.Main

    suspend fun handle(context: Context, entryId: Long, rating: Int) {
        NotificationManagerCompat.from(context).cancel(entryId.toInt())
        outfitRepository.rateEntry(entryId, rating)
        withContext(mainDispatcher) {
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
