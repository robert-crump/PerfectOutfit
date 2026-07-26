package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.perfectoutfit.R

@Composable
fun TemperatureModeToggle(
    useApparent: Boolean,
    onUseApparentSelected: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        stringResource(R.string.temperature_real) to false,
        stringResource(R.string.temperature_apparent) to true
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (label, apparent) ->
            SegmentedButton(
                selected = apparent == useApparent,
                onClick = { onUseApparentSelected(apparent) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                )
            ) {
                Text(label)
            }
        }
    }
}
