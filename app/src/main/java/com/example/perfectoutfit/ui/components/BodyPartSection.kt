package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyPartSection(
    bodyPart: BodyPart,
    items: List<ClothingItem>,
    selectedItemIds: Set<Long> = emptySet(),
    isSelectionMode: Boolean = false,
    onItemClick: ((ClothingItem) -> Unit)? = null,
    onItemLongClick: ((ClothingItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            Text(
                text = "No items",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    ClothingItemChip(
                        name = item.name,
                        isSelected = item.id in selectedItemIds,
                        isSelectionMode = isSelectionMode,
                        onClick = { onItemClick?.invoke(item) },
                        onLongClick = onItemLongClick?.let { { it(item) } }
                    )
                }
            }
        }
    }
}
