package com.example.perfectoutfit.core.model

import androidx.annotation.StringRes
import com.example.perfectoutfit.R

/**
 * How the temperature felt for a logged outfit. Declaration order is the temperature order
 * (too cold < perfect < too hot), so `compareTo` answers "which entry felt warmer".
 * [value] is the Int stored in Room and the export file; nothing else may interpret it.
 */
enum class ComfortRating(
    val value: Int,
    @StringRes val labelRes: Int,
    @StringRes val emojiRes: Int,
    /** Recommendation preference, lowest wins: perfect, then too hot, then too cold. */
    val matchPriority: Int
) {
    TOO_COLD(-1, R.string.rating_too_cold, R.string.rating_emoji_too_cold, 2),
    PERFECT(0, R.string.rating_perfect, R.string.rating_emoji_perfect, 0),
    TOO_HOT(1, R.string.rating_too_hot, R.string.rating_emoji_too_hot, 1);

    companion object {
        /** The rating stored as [value]; null for no rating or a value outside the three. */
        fun fromValue(value: Int?): ComfortRating? = entries.firstOrNull { it.value == value }
    }
}
