package com.example.perfectoutfit.feature.backup

import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException

/** A short, user-readable reason a backup operation failed; Drive error bodies are JSON and too long to show. */
object BackupFailureReason {
    fun of(error: Throwable): String = when (error) {
        is DriveApiException -> "Google Drive returned error ${error.code}"
        is TimeoutCancellationException -> "Google sign-in timed out"
        is IOException -> "Network error" + (error.message?.let { ": $it" } ?: "")
        else -> error.message ?: error::class.simpleName ?: "Unknown error"
    }
}
