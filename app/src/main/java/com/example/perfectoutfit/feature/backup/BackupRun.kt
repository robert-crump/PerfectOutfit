package com.example.perfectoutfit.feature.backup

import com.example.perfectoutfit.core.notification.BackupFailureNotifier
import kotlin.coroutines.cancellation.CancellationException

/** One scheduled backup attempt: runs the service, then raises or clears the failure notification. */
class BackupRun(
    private val service: DriveBackupService,
    private val notifier: BackupFailureNotifier
) {
    /** Returns whether the backup succeeded (uploaded or skipped as unchanged). */
    suspend fun run(): Boolean = try {
        service.backup()
        notifier.clear()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        notifier.showFailure()
        false
    }
}
