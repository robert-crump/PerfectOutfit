package com.example.perfectoutfit.core.notification

/** Tells the user a scheduled Drive backup failed, and takes that notice back after a success. */
interface BackupFailureNotifier {
    fun showFailure()
    fun clear()
}
