package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.perfectoutfit.core.model.ActivityType

/**
 * Single-select row of activity type chips, scrolling horizontally once the chips no longer fit
 * (the last one then peeks in at the edge).
 *
 * Tapping the selected chip calls [onDeselect]; without one (Wardrobe, Explorer) a chip always
 * stays selected. History passes one, so deselecting shows all entries.
 */
@Composable
fun ActivityTypeSelector(
    selectedActivityType: ActivityType?,
    onActivityTypeSelected: (ActivityType) -> Unit,
    modifier: Modifier = Modifier,
    onDeselect: (() -> Unit)? = null,
    activityTypes: List<ActivityType> = ActivityType.entries
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        activityTypes.forEach { activityType ->
            val isSelected = activityType == selectedActivityType
            FilterChip(
                selected = isSelected,
                onClick = {
                    if (!isSelected) onActivityTypeSelected(activityType)
                    else onDeselect?.invoke()
                },
                label = { Text(activityType.displayName) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(activityType.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }
    }
}

// Only two activity types exist so far; repeating them stands in for four chips on a narrow
// phone, to check that the row scrolls and the last chip peeks in.
@Preview(widthDp = 320)
@Composable
private fun ActivityTypeSelectorFourChipsPreview() {
    MaterialTheme {
        Surface {
            ActivityTypeSelector(
                selectedActivityType = ActivityType.entries.first(),
                onActivityTypeSelected = {},
                modifier = Modifier.padding(horizontal = 16.dp),
                activityTypes = ActivityType.entries + ActivityType.entries
            )
        }
    }
}
