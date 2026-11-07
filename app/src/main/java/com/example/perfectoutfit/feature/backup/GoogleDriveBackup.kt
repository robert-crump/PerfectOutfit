package com.example.perfectoutfit.feature.backup

import android.app.Activity
import android.content.Intent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DriveBackup] over Google sign-in ([DriveSession]), the daily [BackupScheduler] and
 * [DriveBackupService]. A singleton, so the account awaiting consent survives the screen's
 * ViewModel being recreated while the consent UI is in front.
 */
@Singleton
class GoogleDriveBackup @Inject constructor(
    private val session: DriveSession,
    private val scheduler: BackupScheduler,
    private val service: DriveBackupService,
    private val state: BackupStateStore
) : DriveBackup {

    /** Signed in, waiting for the consent UI to grant Drive access. */
    private var pendingAccount: String? = null

    override val status: Flow<BackupStatus> =
        combine(session.connectedAccount, state.changes.onStart { emit(Unit) }) { account, _ ->
            BackupStatus(account, state.lastBackupTime, state.lastFailure)
        }.distinctUntilChanged()

    override suspend fun connect(activity: Activity): ConnectResult {
        pendingAccount = null
        val email = session.signIn(activity) ?: return ConnectResult.Cancelled
        return when (val authorization = session.authorize(activity, email)) {
            is DriveAuthorization.Granted -> finishConnect(email)
            is DriveAuthorization.ResolutionRequired -> {
                pendingAccount = email
                ConnectResult.NeedsConsent(authorization.pendingIntent)
            }
        }
    }

    override suspend fun completeConsent(data: Intent): ConnectResult {
        // Nothing is pending once the process was killed while the consent UI was open.
        val email = pendingAccount ?: return ConnectResult.Cancelled
        pendingAccount = null
        session.completeAuthorization(data)
        return finishConnect(email)
    }

    override suspend fun disconnect() {
        scheduler.cancel()
        service.forgetLocalState()
        session.disconnect()
    }

    override suspend fun backupNow(): BackupOutcome = service.backup()

    override suspend fun listSnapshots(): List<BackupSnapshot> = service.listSnapshots()

    override suspend fun restore(snapshotId: String) = service.restore(snapshotId)

    /** Marks [email] connected, schedules the daily backup and runs one right away. */
    private suspend fun finishConnect(email: String): ConnectResult {
        session.markConnected(email)
        // Stale state from an earlier connection must not let the first backup be skipped.
        service.forgetLocalState()
        scheduler.schedule()
        val firstBackup = try {
            Result.success(service.backup())
        } catch (e: TimeoutCancellationException) {
            // A timeout inside the backup, not the caller being cancelled.
            Result.failure(e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        return ConnectResult.Connected(email, firstBackup)
    }
}
