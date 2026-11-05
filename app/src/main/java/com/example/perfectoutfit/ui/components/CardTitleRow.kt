package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A card's headline, with an optional [action] (an icon or text button) at its end. Shared by
 * every titled card, so headlines match across screens. Place it at the card's top edge, outside
 * the content's padding; the content below then starts with 16 dp horizontal padding.
 */
@Composable
fun CardTitleRow(title: String, action: (@Composable () -> Unit)? = null) {
    Row(
        // Keeps a button's height without one, so the body sits alike either way.
        modifier = Modifier
            .padding(start = 16.dp, end = 4.dp, top = 4.dp)
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        action?.invoke()
    }
}
