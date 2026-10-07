package com.example.perfectoutfit.feature.settings

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    // The row whose action is running shows the spinner.
    var busyAction by rememberSaveable { mutableStateOf(DriveAction.CONNECTION) }

    fun start(action: DriveAction, block: () -> Unit) {
        busyAction = action
        block()
    }

    fun spinnerFor(action: DriveAction): (@Composable () -> Unit)? =
        if (state.isBusy && busyAction == action) ({ RowProgress() }) else null

    SettingsGroup(
        {
            SettingsRow(
                icon = Icons.Filled.Cloud,
                title = "Auto-backup to Drive",
                summary = state.account?.let { "Connected as $it" } ?: "Not connected",
                enabled = !state.isBusy,
                onClick = {
                    start(DriveAction.CONNECTION) {
                        if (connected) viewModel.disconnect() else activity?.let(viewModel::connect)
                    }
                },
                trailing = spinnerFor(DriveAction.CONNECTION)
                    ?: { Switch(checked = connected, onCheckedChange = null, enabled = !state.isBusy) }
            )
        },
        {
            SettingsRow(
                icon = Icons.Filled.CloudUpload,
                title = "Back up now",
                summary = "Last backed up: " + (state.lastBackupTime?.let(BackupTimeFormat::format) ?: "never"),
                enabled = connected && !state.isBusy,
                onClick = { start(DriveAction.BACKUP, viewModel::backupNow) },
                trailing = spinnerFor(DriveAction.BACKUP)
            )
        },
        {
            SettingsRow(
                icon = Icons.Filled.CloudDownload,
                title = "Restore from Drive",
                summary = "Pick one of the saved backups",
                enabled = connected && !state.isBusy,
                onClick = { start(DriveAction.RESTORE, viewModel::openRestorePicker) },
                trailing = spinnerFor(DriveAction.RESTORE)
            )
        }
    )

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
            confirmButton = {
                TextButton(onClick = { start(DriveAction.RESTORE, viewModel::confirmRestore) }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissRestoreConfirmation) { Text("Cancel") } }
        )
    }
}

private enum class DriveAction { CONNECTION, BACKUP, RESTORE }

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
