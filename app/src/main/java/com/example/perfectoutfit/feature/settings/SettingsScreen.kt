package com.example.perfectoutfit.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    onNavigateToCatalog: () -> Unit = {},
    onNavigateToLocations: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportData(it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importData(it) }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        Text("Catalog", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = onNavigateToCatalog,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clothing Catalog")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Locations", style = MaterialTheme.typography.titleMedium)

        Button(
            onClick = onNavigateToLocations,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage Locations")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Recommendations", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Use apparent temperature",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = uiState.useApparentTemperature,
                onCheckedChange = viewModel::setUseApparentTemperature
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text("Data Management", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
                    exportLauncher.launch("${date}_perfect_outfit_data.json")
                },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isProcessing
            ) {
                Text("Backup")
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isProcessing
            ) {
                Text("Import")
            }
        }

        if (uiState.isProcessing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        SnackbarHost(hostState = snackbarHostState)
    }
}
