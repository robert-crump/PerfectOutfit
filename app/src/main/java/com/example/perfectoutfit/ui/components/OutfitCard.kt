package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.core.model.BODY_PART_DISPLAY_ORDER
import com.example.perfectoutfit.core.model.OutfitEntryWithDetails

/**
 * A logged outfit as a plain body-part list: how it felt last time, the items per
 * body part, and the notes. With [onEditOutfit] the title row carries a pencil.
 */
@Composable
fun OutfitCard(
    title: String,
    outfit: OutfitEntryWithDetails,
    modifier: Modifier = Modifier,
    onEditOutfit: (() -> Unit)? = null
) {
    val itemsByBodyPart = outfit.clothingItems.groupBy { it.bodyPart }
    val ratingText = ratingLabel(outfit.entry.comfortRating)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            // Keeps the icon button's height without the pencil, so the body sits alike.
            modifier = Modifier
                .padding(start = 16.dp, end = 4.dp, top = 4.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (onEditOutfit != null) {
                IconButton(onClick = onEditOutfit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit outfit",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ratingText.isNotEmpty()) {
                Text(
                    text = "Last time: $ratingText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

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
