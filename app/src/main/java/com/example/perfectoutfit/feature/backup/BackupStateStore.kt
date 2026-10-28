package com.example.perfectoutfit.feature.backup

/** Why the last scheduled backup gave up, and when. */
data class BackupFailure(val time: Long, val reason: String)

/** Persists what the last successful backup looked like, and the last scheduled failure since then. */
interface BackupStateStore {
    var lastBackupTime: Long?
    var lastHash: String?
    var lastFailure: BackupFailure?

    fun clear() {
        lastBackupTime = null
        lastHash = null
        lastFailure = null
    }
}
