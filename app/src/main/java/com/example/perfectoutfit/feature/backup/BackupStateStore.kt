package com.example.perfectoutfit.feature.backup

/** Persists what the last successful backup looked like. */
interface BackupStateStore {
    var lastBackupTime: Long?
    var lastHash: String?

    fun clear() {
        lastBackupTime = null
        lastHash = null
    }
}
