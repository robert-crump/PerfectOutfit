package com.example.perfectoutfit.feature.backup

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.perfectoutfit.core.notification.BackupFailureNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Thin WorkManager wrapper: all decisions live in [DriveBackupService] and [BackupRun]. */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: DriveBackupService,
    private val notifier: BackupFailureNotifier
) : CoroutineWorker(context, params) {

    // A failed run is retried by tomorrow's periodic run rather than with backoff; the user has
    // already been notified.
    override suspend fun doWork(): Result =
        if (BackupRun(service, notifier).run()) Result.success() else Result.failure()
}
