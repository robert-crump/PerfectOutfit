package com.example.perfectoutfit.feature.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.R
import com.example.perfectoutfit.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToCatalog: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Which of Export/Import shows the spinner while the view model is processing.
    var exporting by rememberSaveable { mutableStateOf(false) }
    // A picked file waiting for the user to confirm that it replaces all data.
    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    var choosingTheme by rememberSaveable { mutableStateOf(false) }
    var explainingBackgroundLocation by rememberSaveable { mutableStateOf(false) }
    var explainingRevoke by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshPermissions() }

    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onForegroundLocationResult(granted) }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.onBackgroundLocationResult() }

    LaunchedEffect(viewModel) {
        viewModel.permissionActions.collect { action ->
            when (action) {
                PermissionAction.RequestForegroundLocation ->
                    foregroundLocationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                PermissionAction.RequestBackgroundLocation ->
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                PermissionAction.OpenAppSettings -> context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            exporting = true
            viewModel.exportData(it)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingImport = uri
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                windowInsets = WindowInsets(0)
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionHeader("Appearance")
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.Palette,
                        title = "Theme",
                        summary = uiState.themeMode.displayName,
                        onClick = { choosingTheme = true }
                    )
                }
            )

            SectionHeader("Catalog")
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.Checkroom,
                        title = "Clothing catalog",
                        summary = "Add, edit and organise items",
                        onClick = onNavigateToCatalog,
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) }
                    )
                }
            )

            SectionHeader("Recommendations")
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.Thermostat,
                        title = stringResource(R.string.settings_use_apparent_temperature),
                        summary = "Wind and humidity included. Turn off to match on the actual temperature.",
                        onClick = { viewModel.setUseApparentTemperature(!uiState.useApparentTemperature) },
                        trailing = { Switch(checked = uiState.useApparentTemperature, onCheckedChange = null) }
                    )
                }
            )
            SectionHint("Home and History show both temperatures and highlight the one used for matching.")

            SectionHeader("Widget")
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.MyLocation,
                        title = "Follow my location",
                        summary = "Update the widget's place in the background",
                        // Revoking can't be done in-app, so switching off explains where to do it.
                        onClick = {
                            if (uiState.backgroundLocationGranted) explainingRevoke = true
                            else explainingBackgroundLocation = true
                        },
                        trailing = { Switch(checked = uiState.backgroundLocationGranted, onCheckedChange = null) }
                    )
                }
            )
            SectionHint("Off, the widget shows the place where you last opened the app.")

            SectionHeader("Backup")
            SettingsGroup(
                {
                    SettingsRow(
                        icon = Icons.Filled.Upload,
                        title = "Export",
                        summary = "Save all data to a JSON file",
                        enabled = !uiState.isProcessing,
                        onClick = {
                            val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
                            exportLauncher.launch("${date}_perfect_outfit_data.json")
                        },
                        trailing = if (uiState.isProcessing && exporting) ({ RowProgress() }) else null
                    )
                },
                {
                    SettingsRow(
                        icon = Icons.Filled.Download,
                        title = "Import",
                        summary = "Replace all data from a JSON file",
                        enabled = !uiState.isProcessing,
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        trailing = if (uiState.isProcessing && !exporting) ({ RowProgress() }) else null
                    )
                }
            )

            SectionHeader("Google Drive")
            DriveBackupSection(snackbarHostState)
        }
    }

    if (choosingTheme) {
        ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries,
            selected = uiState.themeMode,
            label = { it.displayName },
            onSelect = {
                viewModel.setThemeMode(it)
                choosingTheme = false
            },
            onDismiss = { choosingTheme = false }
        )
    }

    if (explainingBackgroundLocation) {
        AlertDialog(
            onDismissRequest = { explainingBackgroundLocation = false },
            title = { Text("Location in the background") },
            text = {
                Text(
                    "So the widget shows the weather where you are after you travel, the app reads your " +
                        "location every 30 minutes, also while it's closed. Choose \"Allow all the time\" " +
                        "in the next step. The location is only used to fetch the local forecast."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainingBackgroundLocation = false
                    viewModel.requestBackgroundLocation()
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { explainingBackgroundLocation = false }) { Text("Cancel") }
            }
        )
    }

    if (explainingRevoke) {
        AlertDialog(
            onDismissRequest = { explainingRevoke = false },
            title = { Text("Stop following my location?") },
            text = {
                Text(
                    "Location access can only be changed in system settings. Set it to " +
                        "\"Allow only while using the app\" to stop background updates."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainingRevoke = false
                    viewModel.openAppSettings()
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { explainingRevoke = false }) { Text("Cancel") }
            }
        )
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace all data?") },
            text = { Text("Importing replaces all outfits, clothing items and weather data in the app with the file's contents.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    exporting = false
                    viewModel.importData(uri)
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
            }
        )
    }
}
