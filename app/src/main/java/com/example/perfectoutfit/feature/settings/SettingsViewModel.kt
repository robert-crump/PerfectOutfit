package com.example.perfectoutfit.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.model.ThemeMode
import com.example.perfectoutfit.feature.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val message: String? = null,
    val isProcessing: Boolean = false,
    val useApparentTemperature: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val exportImportManager: ExportImportManager,
    private val preferencesManager: PreferencesManager,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.useApparentTemperature.collect { useApparent ->
                _uiState.value = _uiState.value.copy(useApparentTemperature = useApparent)
            }
        }
        viewModelScope.launch {
            preferencesManager.themeMode.collect { mode ->
                _uiState.value = _uiState.value.copy(themeMode = mode)
            }
        }
    }

    fun setUseApparentTemperature(value: Boolean) {
        viewModelScope.launch {
            preferencesManager.setUseApparentTemperature(value)
            widgetUpdater.redraw()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
            widgetUpdater.redraw()
        }
    }

    fun exportData(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, message = null)
            try {
                val json = exportImportManager.exportToJson()
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray())
                }
                _uiState.value = _uiState.value.copy(isProcessing = false, message = "Data exported successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isProcessing = false, message = "Export failed: ${e.message}")
            }
        }
    }

    fun importData(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, message = null)
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().readText()
                } ?: throw IllegalStateException("Could not read file")
                exportImportManager.importFromJson(json)
                // Imported history changes which widget icons are lit.
                widgetUpdater.redraw()
                _uiState.value = _uiState.value.copy(isProcessing = false, message = "Data imported successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isProcessing = false, message = "Import failed: ${e.message}")
            }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
