package com.example.perfectoutfit.feature.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import kotlinx.coroutines.flow.Flow

/** What the Drive backup screen shows: the connected account and how the last backup went. */
data class BackupStatus(
    /** The connected Google account, or null while disconnected. */
    val account: String? = null,
    val lastBackupTime: Long? = null,
    /** Why the last scheduled backup gave up, until the next successful backup. */
    val lastFailure: BackupFailure? = null
)

/** Where a [DriveBackup.connect] (or its consent follow-up) got to. */
sealed interface ConnectResult {
    /** The user backed out of the account chooser. */
    data object Cancelled : ConnectResult

    /** Launch [pendingIntent], then pass its successful result to [DriveBackup.completeConsent]. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : ConnectResult

    /** Connected and scheduled. The connection stands even when [firstBackup] failed. */
    data class Connected(val email: String, val firstBackup: Result<BackupOutcome>) : ConnectResult
}

/**
 * Everything the Drive backup screen can do. Operations throw on failure, except the first
 * backup after connecting, which [ConnectResult.Connected] reports as data.
 */
interface DriveBackup {
    /** Follows the connected account and the backup state, including writes by the scheduled backup. */
    val status: Flow<BackupStatus>

    /** Sign in, then authorize Drive access; may stop at [ConnectResult.NeedsConsent]. */
    suspend fun connect(activity: Activity): ConnectResult

    /** Finishes a connect after the consent UI returned [data] successfully. */
    suspend fun completeConsent(data: Intent): ConnectResult

    /** Stops the daily backup and forgets local state. The files already in Drive stay. */
    suspend fun disconnect()

    suspend fun backupNow(): BackupOutcome

    /** Backups in Drive, newest first, each marked with whether this app can restore it. */
    suspend fun listSnapshots(): List<BackupSnapshot>

    /** Replaces all data with the snapshot's. */
    suspend fun restore(snapshotId: String)
}
