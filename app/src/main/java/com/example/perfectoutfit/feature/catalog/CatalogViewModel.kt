package com.example.perfectoutfit.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ActivityType
import com.example.perfectoutfit.core.model.ClothingItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository
) : ViewModel() {

    private val _selectedActivityType = MutableStateFlow(ActivityType.CYCLING)
    val selectedActivityType: StateFlow<ActivityType> = _selectedActivityType.asStateFlow()

    val clothingItems: StateFlow<List<ClothingItem>> = selectedActivityType
        .flatMapLatest { activityType -> catalogRepository.getItemsByActivityType(activityType) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dialogState = MutableStateFlow<CatalogDialogState>(CatalogDialogState.Hidden)
    val dialogState: StateFlow<CatalogDialogState> = _dialogState

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedItemIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun selectActivityType(activityType: ActivityType) {
        _selectedActivityType.value = activityType
        clearSelection()
    }

    fun enterSelectionMode(item: ClothingItem) {
        _selectedItemIds.value = setOf(item.id)
    }

    fun toggleItemSelection(item: ClothingItem) {
        val current = _selectedItemIds.value
        _selectedItemIds.value = if (item.id in current) current - item.id else current + item.id
    }

    fun clearSelection() {
        _selectedItemIds.value = emptySet()
    }

    fun showAddDialog(bodyPart: BodyPart? = null) {
        _dialogState.value = CatalogDialogState.Add(bodyPart, setOf(selectedActivityType.value))
    }

    fun showEditDialog(item: ClothingItem) {
        viewModelScope.launch {
            val activityTypes = catalogRepository.activityTypesOf(listOf(item.id))[item.id].orEmpty()
            _dialogState.value = CatalogDialogState.Edit(item, activityTypes)
        }
    }

    fun showDeleteConfirmDialog() {
        viewModelScope.launch {
            val ids = _selectedItemIds.value
            val count = catalogRepository.countEntriesWithItems(ids.toList())
            val otherTypes = catalogRepository.activityTypesOf(ids).values.flatten().toSet() -
                selectedActivityType.value
            _dialogState.value = CatalogDialogState.DeleteConfirm(
                itemNames = clothingItems.value.filter { it.id in ids }.map { it.name },
                otherActivityTypes = ActivityType.entries.filter { it in otherTypes },
                affectedEntryCount = count
            )
        }
    }

    fun dismissDialog() {
        _dialogState.value = CatalogDialogState.Hidden
    }

    fun addItem(bodyPart: BodyPart?, name: String, activityTypes: Set<ActivityType>) {
        if (name.isBlank() || bodyPart == null || activityTypes.isEmpty()) return
        viewModelScope.launch {
            catalogRepository.addItem(bodyPart, name.trim(), activityTypes)
            _dialogState.value = CatalogDialogState.Hidden
        }
    }

    /** Saves name and activity types; unticking the viewed activity type drops the item from this list. */
    fun editItem(item: ClothingItem, newName: String, activityTypes: Set<ActivityType>) {
        if (newName.isBlank() || activityTypes.isEmpty()) return
        viewModelScope.launch {
            catalogRepository.editItem(item, newName.trim(), activityTypes)
            _dialogState.value = CatalogDialogState.Hidden
            clearSelection()
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            catalogRepository.deleteItems(_selectedItemIds.value)
            _selectedItemIds.value = emptySet()
            _dialogState.value = CatalogDialogState.Hidden
        }
    }
}

sealed class CatalogDialogState {
    data object Hidden : CatalogDialogState()
    data class Add(
        val bodyPart: BodyPart? = null,
        /** Pre-ticked: the activity type currently viewed. */
        val activityTypes: Set<ActivityType> = emptySet()
    ) : CatalogDialogState()
    data class Edit(val item: ClothingItem, val activityTypes: Set<ActivityType>) : CatalogDialogState()
    data class DeleteConfirm(
        val itemNames: List<String>,
        /** Activity types other than the viewed one that the items also belong to, in enum order. */
        val otherActivityTypes: List<ActivityType>,
        val affectedEntryCount: Int
    ) : CatalogDialogState() {
        val message: String get() = deleteConfirmationMessage(itemNames, otherActivityTypes, affectedEntryCount)
    }
}

/**
 * E.g. "“Jersey” is also used for Cycling and Running, and appears in 37 outfit entries.
 * Delete it everywhere?" Deleting removes the items from every activity type and entry.
 */
fun deleteConfirmationMessage(
    itemNames: List<String>,
    otherActivityTypes: List<ActivityType>,
    affectedEntryCount: Int
): String {
    val single = itemNames.size == 1
    val subject = if (single) "“${itemNames.single()}”" else "The ${itemNames.size} selected items"
    val entries = when (affectedEntryCount) {
        0 -> "no outfit entries"
        1 -> "1 outfit entry"
        else -> "$affectedEntryCount outfit entries"
    }
    val appears = "${if (single) "appears" else "appear"} in $entries"
    val clauses = if (otherActivityTypes.isEmpty()) appears
    else "${if (single) "is" else "are"} also used for ${joinWithAnd(otherActivityTypes.map { it.displayName })}, and $appears"
    val pronoun = if (single) "it" else "them"
    val question = if (otherActivityTypes.isEmpty() && affectedEntryCount == 0) "Delete $pronoun?"
    else "Delete $pronoun everywhere?"
    return "$subject $clauses. $question"
}

private fun joinWithAnd(words: List<String>): String =
    if (words.size <= 1) words.joinToString()
    else words.dropLast(1).joinToString(", ") + " and " + words.last()
