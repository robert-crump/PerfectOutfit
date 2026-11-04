package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.perfectoutfit.R

/** Returns the emoji character for the given rating value, or null if no rating. */
@Composable
fun ratingEmoji(rating: Int?): String? = when (rating) {
    -1 -> stringResource(R.string.rating_emoji_too_cold)
     0 -> stringResource(R.string.rating_emoji_perfect)
     1 -> stringResource(R.string.rating_emoji_too_hot)
    else -> null
}

/** Returns the label + emoji for the given rating value (empty string if no rating). */
@Composable
fun ratingLabel(rating: Int?): String = when (rating) {
    -1 -> "${stringResource(R.string.rating_too_cold)} ${stringResource(R.string.rating_emoji_too_cold)}"
     0 -> "${stringResource(R.string.rating_perfect)} ${stringResource(R.string.rating_emoji_perfect)}"
     1 -> "${stringResource(R.string.rating_too_hot)} ${stringResource(R.string.rating_emoji_too_hot)}"
    else -> ""
}

private data class RatingOption(val value: Int, val label: String, val emoji: String)

/**
 * Too cold / Perfect / Too hot as one segmented row. Tapping the selected segment again clears
 * the rating, so "no rating" needs no option of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComfortRatingBar(
    selectedRating: Int?,
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        RatingOption(-1, stringResource(R.string.rating_too_cold), stringResource(R.string.rating_emoji_too_cold)),
        RatingOption(0,  stringResource(R.string.rating_perfect),  stringResource(R.string.rating_emoji_perfect)),
        RatingOption(1,  stringResource(R.string.rating_too_hot),  stringResource(R.string.rating_emoji_too_hot))
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            val selected = selectedRating == option.value
            SegmentedButton(
                selected = selected,
                onClick = { onRatingSelected(if (selected) null else option.value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                // The emoji marks the choice, so no checkmark taking label space.
                icon = {},
                label = {
                    Text(
                        text = "${option.emoji} ${option.label}",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
