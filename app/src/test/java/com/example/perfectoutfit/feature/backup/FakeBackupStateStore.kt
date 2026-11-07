package com.example.perfectoutfit.feature.backup

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeBackupStateStore : BackupStateStore {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 64)
    override val changes: Flow<Unit> = _changes

    override var lastBackupTime: Long? = null
        set(value) {
            field = value
            _changes.tryEmit(Unit)
        }
    override var lastHash: String? = null
        set(value) {
            field = value
            _changes.tryEmit(Unit)
        }
    override var lastFailure: BackupFailure? = null
        set(value) {
            field = value
            _changes.tryEmit(Unit)
        }
}
