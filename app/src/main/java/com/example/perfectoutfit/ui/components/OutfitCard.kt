package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.core.model.BODY_PART_DISPLAY_ORDER
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails
import com.example.perfectoutfit.core.model.referenceTemp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * A logged outfit as a plain body-part list: how it felt, when it was worn and at what
 * reference temperature (in [useApparentTemperature]'s basis), the items per body part, and
 * the notes. With [onEditOutfit] the title row carries a pencil.
 */
@Composable
fun OutfitCard(
    title: String,
    outfit: OutfitEntryWithDetails,
    useApparentTemperature: Boolean,
    modifier: Modifier = Modifier,
    onEditOutfit: (() -> Unit)? = null
) {
    val itemsByBodyPart = outfit.clothingItems.groupBy { it.bodyPart }
    val ratingText = ratingLabel(outfit.entry.comfortRating)
    val wornOn = Instant.ofEpochMilli(outfit.entry.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    val wornAt = outfit.weatherSnapshot.referenceTemp(useApparentTemperature).roundToInt()
    val matchText = listOf(ratingText, entryDateLabel(wornOn, LocalDate.now()), "at $wornAt°C")
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        CardTitleRow(
            title = title,
            action = onEditOutfit?.let { onEdit ->
                {
                    IconButton(onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit outfit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = matchText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            BODY_PART_DISPLAY_ORDER.forEach { part ->
                val items = itemsByBodyPart[part]
                if (!items.isNullOrEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = part.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            // Lines its first line up with the items' larger text.
                            modifier = Modifier
                                .width(BODY_PART_LABEL_WIDTH)
                                .padding(top = 2.dp)
                        )
                        Text(
                            text = items.joinToString(", ") { it.name },
                            // The weight goes on the items, not the body part labels.
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            val notes = outfit.entry.notes
            if (notes.isNotEmpty()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Fits "Head and Throat", the longest body part name, on one line. */
private val BODY_PART_LABEL_WIDTH = 112.dp
