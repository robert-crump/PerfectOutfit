package com.example.perfectoutfit.feature.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
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
    /** Why the last scheduled backup gave up, until the next successful backup. */
    val lastFailure: BackupFailure? = null,
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
    private val backup: DriveBackup
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriveBackupUiState())
    val uiState: StateFlow<DriveBackupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            backup.status.collect { status ->
                _uiState.update {
                    it.copy(
                        account = status.account,
                        lastBackupTime = status.lastBackupTime,
                        lastFailure = status.lastFailure
                    )
                }
            }
        }
    }

    /** Sign in, then authorize; consent UI, if needed, continues in [onConsentResult]. */
    fun connect(activity: Activity) = runBusy(CONNECT_FAILED) {
        onConnectResult(backup.connect(activity))
    }

    fun consentLaunched() = _uiState.update { it.copy(consentRequest = null) }

    fun onConsentResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            showMessage("Google Drive access was not granted")
            return
        }
        runBusy(CONNECT_FAILED) { onConnectResult(backup.completeConsent(data)) }
    }

    fun disconnect() = runBusy("Disconnect failed") { backup.disconnect() }

    fun backupNow() = runBusy("Backup failed") {
        showMessage(outcomeMessage(backup.backupNow()))
    }

    fun openRestorePicker() = runBusy("Could not list Drive backups") {
        val snapshots = backup.listSnapshots()
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
            backup.restore(snapshot.id)
            showMessage("Restored the backup from ${BackupTimeFormat.format(snapshot.timestamp)}")
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private fun onConnectResult(result: ConnectResult) {
        when (result) {
            ConnectResult.Cancelled -> Unit
            is ConnectResult.NeedsConsent -> _uiState.update { it.copy(consentRequest = result.pendingIntent) }
            is ConnectResult.Connected -> result.firstBackup
                .onSuccess { showMessage("Connected as ${result.email}. ${outcomeMessage(it)}") }
                .onFailure {
                    showMessage("Connected as ${result.email}, but the first backup failed: ${BackupFailureReason.of(it)}")
                }
        }
    }

    private fun outcomeMessage(outcome: BackupOutcome) = when (outcome) {
        BackupOutcome.UPLOADED -> "Backup uploaded"
        BackupOutcome.SKIPPED -> "Already backed up, nothing has changed"
    }

    private fun showMessage(message: String) = _uiState.update { it.copy(message = message) }

    private fun runBusy(failurePrefix: String, block: suspend () -> Unit) {
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: TimeoutCancellationException) {
                // A sign-in timeout, not the ViewModel being cleared.
                showMessage("$failurePrefix: ${BackupFailureReason.of(e)}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage("$failurePrefix: ${BackupFailureReason.of(e)}")
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private companion object {
        const val CONNECT_FAILED = "Connecting to Google Drive failed"
    }
}
