package com.example.perfectoutfit.feature.settings

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfectoutfit.feature.backup.BackupOutcome
import com.example.perfectoutfit.feature.backup.BackupSnapshot
import com.example.perfectoutfit.feature.backup.BackupStateStore
import com.example.perfectoutfit.feature.backup.BackupTimeFormat
import com.example.perfectoutfit.feature.backup.DriveAuth
import com.example.perfectoutfit.feature.backup.DriveAuthorization
import com.example.perfectoutfit.feature.backup.DriveBackupService
import com.example.perfectoutfit.feature.backup.DriveConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriveBackupUiState(
    /** The connected Google account, or null while disconnected. */
    val account: String? = null,
    val lastBackupTime: Long? = null,
    /** A connect, backup, or restore is in flight. */
    val isBusy: Boolean = false,
    val message: String? = null,
    /** Non-null while the snapshot picker is open. */
    val snapshots: List<BackupSnapshot>? = null,
    /** The snapshot awaiting the user's confirmation to replace all data. */
    val restoreCandidate: BackupSnapshot? = null,
    /** Consent UI the screen must launch, then report back through [DriveBackupViewModel.onConsentResult]. */
    val consentRequest: PendingIntent? = null
)

@HiltViewModel
class DriveBackupViewModel @Inject constructor(
    private val auth: DriveAuth,
    private val connection: DriveConnection,
    private val service: DriveBackupService,
    private val state: BackupStateStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveBackupUiState(lastBackupTime = state.lastBackupTime))
    val uiState: StateFlow<DriveBackupUiState> = _uiState.asStateFlow()

    private var pendingAccount: String? = null

    init {
        viewModelScope.launch {
            auth.connectedAccount.collect { account ->
                _uiState.update { it.copy(account = account, lastBackupTime = state.lastBackupTime) }
            }
        }
    }

    /** Sign in, then authorize; consent UI, if needed, continues in [onConsentResult]. */
    fun connect(activity: Activity) = runBusy("Connecting to Google Drive failed") {
        val email = auth.signIn(activity) ?: return@runBusy
        when (val authorization = auth.authorize(activity, email)) {
            is DriveAuthorization.Granted -> finishConnect(email)
            is DriveAuthorization.ResolutionRequired -> {
                pendingAccount = email
                _uiState.update { it.copy(consentRequest = authorization.pendingIntent) }
            }
        }
    }

    fun consentLaunched() = _uiState.update { it.copy(consentRequest = null) }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        val email = pendingAccount ?: return
        pendingAccount = null
        if (resultCode != Activity.RESULT_OK || data == null) {
            showMessage("Google Drive access was not granted")
            return
        }
        runBusy("Connecting to Google Drive failed") {
            auth.completeAuthorization(data)
            finishConnect(email)
        }
    }

    fun disconnect() = runBusy("Disconnect failed") {
        connection.disconnect()
        refreshLastBackup()
    }

    fun backupNow() = runBusy("Backup failed") {
        connection.backupNow()
            .onSuccess { showMessage(outcomeMessage(it)) }
            .onFailure { showMessage("Backup failed: ${it.message}") }
        refreshLastBackup()
    }

    fun openRestorePicker() = runBusy("Could not list Drive backups") {
        val snapshots = service.listSnapshots()
        if (snapshots.isEmpty()) {
            showMessage("No backups found in Drive")
        } else {
            _uiState.update { it.copy(snapshots = snapshots) }
        }
    }

    fun dismissRestorePicker() = _uiState.update { it.copy(snapshots = null) }

    fun chooseSnapshot(snapshot: BackupSnapshot) {
        if (!snapshot.restorable) return
        _uiState.update { it.copy(snapshots = null, restoreCandidate = snapshot) }
    }

    fun dismissRestoreConfirmation() = _uiState.update { it.copy(restoreCandidate = null) }

    fun confirmRestore() {
        val snapshot = _uiState.value.restoreCandidate ?: return
        _uiState.update { it.copy(restoreCandidate = null) }
        runBusy("Restore failed") {
            service.restore(snapshot.id)
            showMessage("Restored the backup from ${BackupTimeFormat.format(snapshot.timestamp)}")
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private suspend fun finishConnect(email: String) {
        connection.completeConnect(email)
            .onSuccess { showMessage("Connected as $email. ${outcomeMessage(it)}") }
            .onFailure { showMessage("Connected as $email, but the first backup failed: ${it.message}") }
        refreshLastBackup()
    }

    private fun outcomeMessage(outcome: BackupOutcome) = when (outcome) {
        BackupOutcome.UPLOADED -> "Backup uploaded"
        BackupOutcome.SKIPPED -> "Already backed up, nothing has changed"
    }

    private fun refreshLastBackup() = _uiState.update { it.copy(lastBackupTime = state.lastBackupTime) }

    private fun showMessage(message: String) = _uiState.update { it.copy(message = message) }

    private fun runBusy(failurePrefix: String, block: suspend () -> Unit) {
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("$failurePrefix: ${e.message}")
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }
}
