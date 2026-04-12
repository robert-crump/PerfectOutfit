package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

private data class RatingOption(val value: Int?, val label: String, val emoji: String?)

@Composable
fun ComfortRatingBar(
    selectedRating: Int?,
    onRatingSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val resolvedOptions = listOf(
        RatingOption(null, "No rating", null),
        RatingOption(-1, stringResource(R.string.rating_too_cold), stringResource(R.string.rating_emoji_too_cold)),
        RatingOption(0,  stringResource(R.string.rating_perfect),  stringResource(R.string.rating_emoji_perfect)),
        RatingOption(1,  stringResource(R.string.rating_too_hot),  stringResource(R.string.rating_emoji_too_hot))
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "How did the outfit feel?",
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            resolvedOptions.forEach { option ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RadioButton(
                        selected = selectedRating == option.value,
                        onClick = { onRatingSelected(option.value) }
                    )
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                    if (option.emoji != null) {
                        Text(
                            text = option.emoji,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
