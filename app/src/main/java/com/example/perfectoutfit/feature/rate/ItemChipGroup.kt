package com.example.perfectoutfit.feature.rate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.core.model.ClothingItem

/**
 * Clothing items as toggle chips, the one chip style for picking an outfit's items: deselected
 * chips are outlined and transparent, selected ones filled with primaryContainer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemChipGroup(
    items: List<ClothingItem>,
    selectedItemIds: Set<Long>,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val border = FilterChipDefaults.filterChipBorder(
        enabled = true,
        selected = false,
        borderColor = MaterialTheme.colorScheme.outline,
        selectedBorderColor = Color.Transparent
    )
    val colors = FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = MaterialTheme.colorScheme.onSurface,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = item.id in selectedItemIds,
                onClick = { onToggle(item.id) },
                label = { Text(item.name) },
                border = border,
                colors = colors
            )
        }
    }
}
