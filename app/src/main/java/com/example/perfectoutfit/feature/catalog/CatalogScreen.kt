package com.example.perfectoutfit.feature.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.core.model.BODY_PART_DISPLAY_ORDER
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.ui.components.BodyPartSection
import com.example.perfectoutfit.ui.components.SportToggle
import com.example.perfectoutfit.ui.components.verticalScrollbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: CatalogViewModel = hiltViewModel()
) {
    val selectedSport by viewModel.selectedSport.collectAsStateWithLifecycle()
    val items by viewModel.clothingItems.collectAsStateWithLifecycle()
    val dialogState by viewModel.dialogState.collectAsStateWithLifecycle()
    val selectedItemIds by viewModel.selectedItemIds.collectAsStateWithLifecycle()
    val isSelectionMode by viewModel.isSelectionMode.collectAsStateWithLifecycle()

    // All categories collapsed by default.
    var expandedCategories by remember { mutableStateOf<Set<BodyPart>>(emptySet()) }

    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.showAddDialog() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Clothing item") }
                )
            }
        },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedItemIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        val singleSelectedItem = if (selectedItemIds.size == 1)
                            items.find { it.id == selectedItemIds.first() } else null
                        IconButton(
                            onClick = {
                                singleSelectedItem?.let { viewModel.showRenameDialog(it) }
                            },
                            enabled = singleSelectedItem != null
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Rename")
                        }
                        IconButton(onClick = viewModel::showDeleteConfirmDialog) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    windowInsets = WindowInsets(0)
                )
            } else {
                TopAppBar(
                    title = { Text("Clothing Catalog", style = MaterialTheme.typography.headlineMedium) },
                    navigationIcon = {
                        if (onNavigateBack != null) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets(0)
                )
            }
        }
    ) { innerPadding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScrollbar(scrollState)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SportToggle(
                selectedSport = selectedSport,
                onSportSelected = viewModel::selectSport,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            BODY_PART_DISPLAY_ORDER.forEach { bodyPart ->
                val bodyPartItems = items.filter { it.bodyPart == bodyPart }
                val isExpanded = bodyPart in expandedCategories

                Column {
                    val toggleCategory = {
                        expandedCategories = if (isExpanded)
                            expandedCategories - bodyPart
                        else
                            expandedCategories + bodyPart
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { toggleCategory() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            bodyPart.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { toggleCategory() }) {
                            Icon(
                                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (isExpanded) "Collapse" else "Expand"
                            )
                        }
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        BodyPartSection(
                            bodyPart = bodyPart,
                            items = bodyPartItems,
                            selectedItemIds = selectedItemIds,
                            isSelectionMode = isSelectionMode,
                            onItemClick = { item ->
                                if (isSelectionMode) viewModel.toggleItemSelection(item)
                            },
                            onItemLongClick = { item ->
                                if (!isSelectionMode) viewModel.enterSelectionMode(item)
                                else viewModel.toggleItemSelection(item)
                            }
                        )
                    }
                }
            }
        }
    }

    when (val state = dialogState) {
        is CatalogDialogState.Add -> {
            var text by remember { mutableStateOf("") }
            var selectedBodyPart by remember { mutableStateOf(state.bodyPart) }
            var bodyPartExpanded by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = {
                    Text(
                        if (selectedBodyPart != null)
                            "Add Item to ${selectedBodyPart!!.displayName}"
                        else
                            "Add Item"
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (state.bodyPart == null) {
                            // FAB mode: user picks a body part
                            ExposedDropdownMenuBox(
                                expanded = bodyPartExpanded,
                                onExpandedChange = { bodyPartExpanded = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedBodyPart?.displayName ?: "Select category",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Category") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(bodyPartExpanded)
                                    }
                                )
                                ExposedDropdownMenu(
                                    expanded = bodyPartExpanded,
                                    onDismissRequest = { bodyPartExpanded = false }
                                ) {
                                    BODY_PART_DISPLAY_ORDER.forEach { bp ->
                                        DropdownMenuItem(
                                            text = { Text(bp.displayName) },
                                            onClick = {
                                                selectedBodyPart = bp
                                                bodyPartExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Item name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.addItem(selectedBodyPart, text) },
                        enabled = selectedBodyPart != null && text.isNotBlank()
                    ) {
                        Text("Add")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") }
                }
            )
        }

        is CatalogDialogState.Rename -> {
            var text by remember(state.item.id) { mutableStateOf(state.item.name) }
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text("Rename Item") },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("New name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.renameItem(state.item, text) }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") }
                }
            )
        }

        is CatalogDialogState.DeleteConfirm -> {
            val affectedCount = state.affectedEntryCount
            AlertDialog(
                onDismissRequest = viewModel::dismissDialog,
                title = { Text("Delete Items") },
                text = {
                    val entryText = if (affectedCount == 0)
                        "No outfit ratings reference these items."
                    else if (affectedCount == 1)
                        "1 outfit rating references these items and will be affected."
                    else
                        "$affectedCount outfit ratings reference these items and will be affected."
                    Text(
                        "The selected ${selectedItemIds.size} item(s) will be permanently deleted. " +
                            "$entryText Continue?"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = viewModel::deleteSelectedItems,
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDialog) { Text("Cancel") }
                }
            )
        }

        CatalogDialogState.Hidden -> {}
    }
}
