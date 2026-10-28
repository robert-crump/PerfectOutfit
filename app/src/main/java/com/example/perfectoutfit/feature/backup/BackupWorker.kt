package com.example.perfectoutfit.feature.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.perfectoutfit.core.notification.BackupFailureNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Clock

/** Thin WorkManager wrapper: all decisions live in [DriveBackupService] and [BackupRun]. */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: DriveBackupService,
    private val notifier: BackupFailureNotifier,
    private val state: BackupStateStore,
    private val clock: Clock
) : CoroutineWorker(context, params) {

    // A transient error is retried with the backoff set in [WorkManagerBackupScheduler]. Once
    // [BackupRun] gives up, tomorrow's periodic run tries again; the user has been notified.
    override suspend fun doWork(): Result =
        when (val result = BackupRun(service, notifier, state, clock).run(runAttemptCount)) {
            BackupRunResult.Success -> Result.success()
            is BackupRunResult.Retry -> {
                Log.w(TAG, "Backup attempt ${runAttemptCount + 1} failed, retrying", result.error)
                Result.retry()
            }
            is BackupRunResult.Failed -> {
                Log.e(TAG, "Backup failed after ${runAttemptCount + 1} attempt(s)", result.error)
                Result.failure()
            }
        }

    private companion object {
        const val TAG = "BackupWorker"
    }
}
