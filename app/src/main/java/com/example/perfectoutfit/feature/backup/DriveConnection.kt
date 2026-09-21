package com.example.perfectoutfit.feature.backup

import com.example.perfectoutfit.core.notification.BackupFailureNotifier
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What happens once the user has signed in and granted Drive access, and when they turn Drive
 * backup off. Sign-in and consent need an Activity, so they stay with [DriveAuth]; this class
 * owns everything after them.
 */
@Singleton
class DriveConnection @Inject constructor(
    private val session: DriveSession,
    private val scheduler: BackupScheduler,
    private val service: DriveBackupService,
    private val notifier: BackupFailureNotifier
) {
    /**
     * Marks [accountEmail] connected, schedules the daily backup and runs one backup right away.
     * The connection stands even if that first backup fails; the failure is returned for the UI.
     */
    suspend fun completeConnect(accountEmail: String): Result<BackupOutcome> {
        session.markConnected(accountEmail)
        // Stale state from an earlier connection must not let the first backup be skipped.
        service.onDisconnect()
        scheduler.schedule()
        return backupNow()
    }

    /** Stops the daily backup and forgets local state. The files already in Drive stay. */
    suspend fun disconnect() {
        scheduler.cancel()
        service.onDisconnect()
        notifier.clear()
        session.disconnect()
    }

    /** A user-initiated backup. Success clears any earlier failure notification. */
    suspend fun backupNow(): Result<BackupOutcome> = try {
        Result.success(service.backup()).also { notifier.clear() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
