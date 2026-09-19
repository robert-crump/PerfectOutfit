package com.example.perfectoutfit.feature.backup

class FakeBackupStateStore : BackupStateStore {
    override var lastBackupTime: Long? = null
    override var lastHash: String? = null
}
