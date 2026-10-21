package com.example.perfectoutfit.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Same look as the Settings screens of Plantry and Hue and You.

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
    )
}

@Composable
internal fun SectionHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 32.dp)
    )
}

private val GroupOuterCorner = 24.dp
private val GroupInnerCorner = 4.dp

/** Rounded rows separated by a small gap; only the outer corners of the first and last row are large. */
@Composable
internal fun SettingsGroup(vararg rows: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rows.forEachIndexed { index, row ->
            val top = if (index == 0) GroupOuterCorner else GroupInnerCorner
            val bottom = if (index == rows.lastIndex) GroupOuterCorner else GroupInnerCorner
            Surface(
                shape = RoundedCornerShape(top, top, bottom, bottom),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                row()
            }
        }
    }
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    summary: String? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val disabled = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ALPHA)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it, color = if (enabled) Color.Unspecified else disabled) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        colors = if (enabled) {
            ListItemDefaults.colors(containerColor = Color.Transparent)
        } else {
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                headlineColor = disabled,
                leadingIconColor = disabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick)
    )
}

/** Trailing spinner for the row whose action is running. */
@Composable
internal fun RowProgress() {
    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
}

/** Radio list that applies a choice as soon as it is tapped. */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) }
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null, modifier = Modifier.padding(12.dp))
                        Text(label(option), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private const val DISABLED_ALPHA = 0.38f
