package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.perfectoutfit.core.model.ActivityType

@Composable
fun ActivityTypeToggle(
    selectedActivityType: ActivityType,
    onActivityTypeSelected: (ActivityType) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ActivityType.entries.forEachIndexed { index, activityType ->
            SegmentedButton(
                selected = activityType == selectedActivityType,
                onClick = { onActivityTypeSelected(activityType) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = ActivityType.entries.size
                ),
                icon = {
                    if (activityType == selectedActivityType) {
                        Icon(
                            painter = painterResource(activityType.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                    } else {
                        SegmentedButtonDefaults.Icon(active = false)
                    }
                }
            ) {
                Text(activityType.displayName)
            }
        }
    }
}
