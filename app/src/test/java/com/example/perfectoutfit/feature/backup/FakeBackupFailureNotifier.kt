package com.example.perfectoutfit.feature.backup

import com.example.perfectoutfit.core.notification.BackupFailureNotifier

class FakeBackupFailureNotifier : BackupFailureNotifier {
    var visible = false
        private set
    var shown = 0
        private set

    override fun showFailure() {
        visible = true
        shown++
    }

    override fun clear() {
        visible = false
    }
}
