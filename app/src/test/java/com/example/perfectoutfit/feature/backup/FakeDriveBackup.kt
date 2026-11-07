package com.example.perfectoutfit.feature.backup

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** [DriveBackup] with scripted answers; every operation waits for [gate] and then throws [error] if set. */
class FakeDriveBackup : DriveBackup {
    override val status = MutableStateFlow(BackupStatus())

    var connectResult: ConnectResult = ConnectResult.Cancelled
    var consentResult: ConnectResult = ConnectResult.Cancelled
    var backupOutcome = BackupOutcome.UPLOADED
    var snapshots: List<BackupSnapshot> = emptyList()

    var error: Exception? = null

    /** While set and incomplete, operations stay in flight. */
    var gate: CompletableDeferred<Unit>? = null

    val consents = mutableListOf<Intent>()
    val restored = mutableListOf<String>()
    var disconnects = 0
        private set

    override suspend fun connect(activity: Activity): ConnectResult = step { connectResult }

    override suspend fun completeConsent(data: Intent): ConnectResult = step {
        consents += data
        consentResult
    }

    override suspend fun disconnect() = step { disconnects += 1 }

    override suspend fun backupNow(): BackupOutcome = step { backupOutcome }

    override suspend fun listSnapshots(): List<BackupSnapshot> = step { snapshots }

    override suspend fun restore(snapshotId: String) = step { restored += snapshotId }

    private suspend fun <T> step(answer: () -> T): T {
        gate?.await()
        error?.let { throw it }
        return answer()
    }
}
