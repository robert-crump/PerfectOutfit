package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

private val OPTIONS = listOf("Temperature" to false, "Feels like" to true)

@Composable
fun TemperatureModeToggle(
    useApparent: Boolean,
    onUseApparentSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        OPTIONS.forEachIndexed { index, (label, apparent) ->
            SegmentedButton(
                selected = apparent == useApparent,
                onClick = { onUseApparentSelected(apparent) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = OPTIONS.size
                )
            ) {
                Text(label)
            }
        }
    }
}
