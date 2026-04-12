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
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.Sport

@Composable
fun SportToggle(
    selectedSport: Sport,
    onSportSelected: (Sport) -> Unit,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        Sport.entries.forEachIndexed { index, sport ->
            val iconRes = when (sport) {
                Sport.CYCLING -> R.drawable.ic_bike
                Sport.RUNNING -> R.drawable.ic_sprint
            }
            SegmentedButton(
                selected = sport == selectedSport,
                onClick = { onSportSelected(sport) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = Sport.entries.size
                ),
                icon = {
                    if (sport == selectedSport) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                        )
                    } else {
                        SegmentedButtonDefaults.Icon(active = false)
                    }
                }
            ) {
                Text(sport.name.lowercase().replaceFirstChar { it.uppercase() })
            }
        }
    }
}
