package com.example.perfectoutfit.feature.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.Sport
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

    private val _selectedSport = MutableStateFlow(Sport.CYCLING)
    val selectedSport: StateFlow<Sport> = _selectedSport.asStateFlow()

    val clothingItems: StateFlow<List<ClothingItem>> = selectedSport
        .flatMapLatest { sport -> catalogRepository.getItemsBySport(sport) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dialogState = MutableStateFlow<CatalogDialogState>(CatalogDialogState.Hidden)
    val dialogState: StateFlow<CatalogDialogState> = _dialogState

    private val _selectedItemIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedItemIds: StateFlow<Set<Long>> = _selectedItemIds.asStateFlow()

    val isSelectionMode: StateFlow<Boolean> = _selectedItemIds
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun selectSport(sport: Sport) {
        _selectedSport.value = sport
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
        _dialogState.value = CatalogDialogState.Add(bodyPart)
    }

    fun showRenameDialog(item: ClothingItem) {
        _dialogState.value = CatalogDialogState.Rename(item)
    }

    fun showDeleteConfirmDialog() {
        viewModelScope.launch {
            val count = catalogRepository.countEntriesWithItems(_selectedItemIds.value.toList())
            _dialogState.value = CatalogDialogState.DeleteConfirm(count)
        }
    }

    fun dismissDialog() {
        _dialogState.value = CatalogDialogState.Hidden
    }

    fun addItem(bodyPart: BodyPart?, name: String) {
        if (name.isBlank() || bodyPart == null) return
        viewModelScope.launch {
            catalogRepository.addItem(selectedSport.value, bodyPart, name.trim())
            _dialogState.value = CatalogDialogState.Hidden
        }
    }

    fun renameItem(item: ClothingItem, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            catalogRepository.renameItem(item, newName.trim())
            _dialogState.value = CatalogDialogState.Hidden
        }
    }

    fun deleteSelectedItems() {
        viewModelScope.launch {
            _selectedItemIds.value.forEach { id -> catalogRepository.deleteItem(id) }
            _selectedItemIds.value = emptySet()
            _dialogState.value = CatalogDialogState.Hidden
        }
    }
}

sealed class CatalogDialogState {
    data object Hidden : CatalogDialogState()
    data class Add(val bodyPart: BodyPart? = null) : CatalogDialogState()
    data class Rename(val item: ClothingItem) : CatalogDialogState()
    data class DeleteConfirm(val affectedEntryCount: Int = 0) : CatalogDialogState()
}
