package com.example.perfectoutfit.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.perfectoutfit.feature.backup.BackupSnapshot
import com.example.perfectoutfit.feature.backup.BackupTimeFormat
import com.example.perfectoutfit.feature.backup.SnapshotCompatibility

@Composable
fun DriveBackupSection(
    snackbarHostState: SnackbarHostState,
    viewModel: DriveBackupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onConsentResult(result.resultCode, result.data)
    }

    LaunchedEffect(state.consentRequest) {
        state.consentRequest?.let {
            consentLauncher.launch(IntentSenderRequest.Builder(it).build())
            viewModel.consentLaunched()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val connected = state.account != null

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Google Drive Backup", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Auto-backup to Drive", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = state.account?.let { "Connected as $it" } ?: "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = connected,
                enabled = !state.isBusy,
                onCheckedChange = { enable ->
                    if (enable) activity?.let(viewModel::connect) else viewModel.disconnect()
                }
            )
        }

        if (connected) {
            Text(
                text = "Last backed up: " + (state.lastBackupTime?.let(BackupTimeFormat::format) ?: "never"),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = viewModel::backupNow,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isBusy
                ) {
                    Text("Back up now")
                }
                OutlinedButton(
                    onClick = viewModel::openRestorePicker,
                    modifier = Modifier.weight(1f),
                    enabled = !state.isBusy
                ) {
                    Text("Restore from Drive")
                }
            }
        }

        if (state.isBusy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }

    state.snapshots?.let { snapshots ->
        SnapshotPickerDialog(
            snapshots = snapshots,
            onSelect = viewModel::chooseSnapshot,
            onDismiss = viewModel::dismissRestorePicker
        )
    }

    state.restoreCandidate?.let { snapshot ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreConfirmation,
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Restore the backup from ${BackupTimeFormat.format(snapshot.timestamp)}? " +
                        "This replaces all current data in the app."
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("Restore") } },
            dismissButton = { TextButton(onClick = viewModel::dismissRestoreConfirmation) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SnapshotPickerDialog(
    snapshots: List<BackupSnapshot>,
    onSelect: (BackupSnapshot) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from Drive") },
        text = {
            LazyColumn {
                items(snapshots, key = { it.id }) { snapshot ->
                    ListItem(
                        headlineContent = { Text(BackupTimeFormat.format(snapshot.timestamp)) },
                        supportingContent = incompatibleLabel(snapshot)?.let { label -> { Text(label) } },
                        modifier = Modifier
                            .alpha(if (snapshot.restorable) 1f else 0.5f)
                            .clickable(enabled = snapshot.restorable) { onSelect(snapshot) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun incompatibleLabel(snapshot: BackupSnapshot): String? = when (snapshot.compatibility) {
    SnapshotCompatibility.COMPATIBLE -> null
    SnapshotCompatibility.NEWER_VERSION -> "Made by a newer app version. Update the app to restore it."
    SnapshotCompatibility.UNREADABLE -> "Not a valid backup file"
}
