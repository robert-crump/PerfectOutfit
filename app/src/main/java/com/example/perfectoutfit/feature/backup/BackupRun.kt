package com.example.perfectoutfit.feature.backup

import com.example.perfectoutfit.core.notification.BackupFailureNotifier
import kotlinx.coroutines.TimeoutCancellationException
import java.time.Clock
import kotlin.coroutines.cancellation.CancellationException

/** What the scheduler should do after one [BackupRun]. */
sealed interface BackupRunResult {
    data object Success : BackupRunResult

    /** A transient error; try again after backoff. Nothing is shown to the user yet. */
    data class Retry(val error: Exception) : BackupRunResult

    /** Gave up: the user has been notified and the reason recorded. */
    data class Failed(val error: Exception) : BackupRunResult
}

/**
 * One scheduled backup attempt: runs the service, then decides whether a failure is worth retrying
 * or should be reported. Transient errors (network, timeouts, Drive server errors) are retried
 * silently until [MAX_ATTEMPTS]; errors a retry cannot fix are reported right away.
 */
class BackupRun(
    private val service: DriveBackupService,
    private val notifier: BackupFailureNotifier,
    private val state: BackupStateStore,
    private val clock: Clock
) {
    /** [attempt] counts earlier tries of this run, starting at 0. */
    suspend fun run(attempt: Int = 0): BackupRunResult = try {
        service.backup()
        notifier.clear()
        BackupRunResult.Success
    } catch (e: TimeoutCancellationException) {
        // A timeout inside the backup, not the worker being stopped.
        onError(e, attempt)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e, attempt)
    }

    private fun onError(error: Exception, attempt: Int): BackupRunResult {
        if (isTransient(error) && attempt + 1 < MAX_ATTEMPTS) return BackupRunResult.Retry(error)
        state.lastFailure = BackupFailure(clock.millis(), reasonFor(error))
        notifier.showFailure()
        return BackupRunResult.Failed(error)
    }

    companion object {
        /** The first try plus retries; with WorkManager's 15-minute exponential backoff that spans ~1¾ hours. */
        const val MAX_ATTEMPTS = 4

        /** Unknown errors count as transient: a few wasted retries beat a false alarm. */
        fun isTransient(error: Exception): Boolean = when (error) {
            is DriveReconnectRequiredException -> false
            is DriveApiException -> error.code >= 500 || error.code == 408 || error.code == 429
            else -> true
        }

        /** A short, user-readable reason; Drive error bodies are JSON and too long to show. */
        fun reasonFor(error: Exception): String = when (error) {
            is DriveApiException -> "Google Drive returned error ${error.code}"
            is TimeoutCancellationException -> "Google sign-in timed out"
            is java.io.IOException -> "Network error" + (error.message?.let { ": $it" } ?: "")
            else -> error.message ?: error::class.simpleName ?: "Unknown error"
        }
    }
}
