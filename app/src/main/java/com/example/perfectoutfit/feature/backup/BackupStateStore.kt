package com.example.perfectoutfit.feature.backup

import kotlinx.coroutines.flow.Flow

/** Why the last scheduled backup gave up, and when. */
data class BackupFailure(val time: Long, val reason: String)

/** Persists what the last successful backup looked like, and the last scheduled failure since then. */
interface BackupStateStore {
    var lastBackupTime: Long?
    var lastHash: String?
    var lastFailure: BackupFailure?

    /** Emits after any of the above is written, including by the scheduled backup. */
    val changes: Flow<Unit>

    fun clear() {
        lastBackupTime = null
        lastHash = null
        lastFailure = null
    }
}
