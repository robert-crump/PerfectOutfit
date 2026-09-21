package com.example.perfectoutfit.feature.backup

/** Turns the daily Drive backup on and off. */
interface BackupScheduler {
    fun schedule()
    fun cancel()
}
