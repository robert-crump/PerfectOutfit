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
import com.example.perfectoutfit.core.model.ComfortRating

/** Returns the emoji character for the given rating, or null if no rating. */
@Composable
fun ratingEmoji(rating: ComfortRating?): String? = rating?.let { stringResource(it.emojiRes) }

/** Returns the label + emoji for the given rating (empty string if no rating). */
@Composable
fun ratingLabel(rating: ComfortRating?): String =
    rating?.let { "${stringResource(it.labelRes)} ${stringResource(it.emojiRes)}" } ?: ""

/**
 * Too cold / Perfect / Too hot as one segmented row. Tapping the selected segment again clears
 * the rating, so "no rating" needs no option of its own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComfortRatingBar(
    selectedRating: ComfortRating?,
    onRatingSelected: (ComfortRating?) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ComfortRating.entries.forEachIndexed { index, option ->
            val selected = selectedRating == option
            SegmentedButton(
                selected = selected,
                onClick = { onRatingSelected(if (selected) null else option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ComfortRating.entries.size),
                // The emoji marks the choice, so no checkmark taking label space.
                icon = {},
                label = {
                    Text(
                        text = "${stringResource(option.emojiRes)} ${stringResource(option.labelRes)}",
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
