package com.example.perfectoutfit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.perfectoutfit.core.model.FavoriteLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationDropdown(
    selectedLocationName: String,
    favoriteLocations: List<FavoriteLocation>,
    onCurrentLocationSelected: () -> Unit,
    onFavoriteSelected: (FavoriteLocation) -> Unit,
    onAddLocation: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLocationName,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = {
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Select location")
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Current Location") },
                onClick = {
                    onCurrentLocationSelected()
                    expanded = false
                }
            )

            favoriteLocations.forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.name) },
                    onClick = {
                        onFavoriteSelected(location)
                        expanded = false
                    }
                )
            }

            DropdownMenuItem(
                text = { Text("Add location") },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null)
                },
                colors = MenuDefaults.itemColors(
                    leadingIconColor = MaterialTheme.colorScheme.onSurface
                ),
                onClick = {
                    onAddLocation()
                    expanded = false
                }
            )
        }
    }
}
