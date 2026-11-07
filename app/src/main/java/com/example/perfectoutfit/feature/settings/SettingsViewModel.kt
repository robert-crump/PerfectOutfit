package com.example.perfectoutfit.feature.settings

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.core.datastore.PreferencesManager
import com.example.perfectoutfit.core.exportimport.ExportImportManager
import com.example.perfectoutfit.core.location.BackgroundLocationStep
import com.example.perfectoutfit.core.location.LocationPermissions
import com.example.perfectoutfit.core.location.nextBackgroundLocationStep
import com.example.perfectoutfit.core.model.ThemeMode
import com.example.perfectoutfit.feature.recommendation.ModeComparisonLine
import com.example.perfectoutfit.feature.recommendation.Recommendations
import com.example.perfectoutfit.feature.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val message: String? = null,
    val isProcessing: Boolean = false,
    val useApparentTemperature: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val backgroundLocationGranted: Boolean = false,
    /** The overall feels-like vs actual line; null until worked out. */
    val modeComparison: ModeComparisonLine? = null
)

/** A system screen or dialog the Settings screen opens for the background-location switch. */
enum class PermissionAction {
    RequestForegroundLocation,
    RequestBackgroundLocation,
    OpenAppSettings
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val exportImportManager: ExportImportManager,
    private val documentFiles: DocumentFiles,
    private val preferencesManager: PreferencesManager,
    private val widgetUpdater: WidgetUpdater,
    private val locationPermissions: LocationPermissions,
    private val recommendations: Recommendations
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(backgroundLocationGranted = locationPermissions.hasBackground())
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _permissionActions = Channel<PermissionAction>(Channel.BUFFERED)
    val permissionActions: Flow<PermissionAction> = _permissionActions.receiveAsFlow()

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
        viewModelScope.launch {
            recommendations.modeComparison().collect { comparison ->
                _uiState.value = _uiState.value.copy(modeComparison = comparison.overall)
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

    /** The switch mirrors the OS grant, which the user may change in system settings meanwhile. */
    fun refreshPermissions() {
        _uiState.value = _uiState.value.copy(backgroundLocationGranted = locationPermissions.hasBackground())
    }

    fun requestBackgroundLocation() {
        val step = nextBackgroundLocationStep(
            Build.VERSION.SDK_INT,
            foregroundGranted = locationPermissions.hasForeground(),
            backgroundGranted = locationPermissions.hasBackground()
        )
        when (step) {
            BackgroundLocationStep.AlreadyGranted -> refreshPermissions()
            BackgroundLocationStep.RequestForegroundFirst -> emit(PermissionAction.RequestForegroundLocation)
            BackgroundLocationStep.RequestBackgroundInline -> emit(PermissionAction.RequestBackgroundLocation)
            BackgroundLocationStep.DirectToSystemSettings -> emit(PermissionAction.OpenAppSettings)
        }
    }

    /** Result of the foreground request that [requestBackgroundLocation] started; continues with the next step. */
    fun onForegroundLocationResult(granted: Boolean) {
        if (granted) requestBackgroundLocation()
    }

    fun onBackgroundLocationResult() = refreshPermissions()

    /** Revoking can't be done in-app; the user is sent to the app's system settings. */
    fun openAppSettings() = emit(PermissionAction.OpenAppSettings)

    private fun emit(action: PermissionAction) {
        _permissionActions.trySend(action)
    }

    /** Writes all data to the document at [uri], a content URI the user picked. */
    fun exportData(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, message = null)
            try {
                documentFiles.writeText(uri, exportImportManager.exportToJson())
                _uiState.value = _uiState.value.copy(isProcessing = false, message = "Data exported successfully")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isProcessing = false, message = "Export failed: ${e.message}")
            }
        }
    }

    /** Replaces all data with the document at [uri], a content URI the user picked. */
    fun importData(uri: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, message = null)
            try {
                exportImportManager.importFromJson(documentFiles.readText(uri))
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
