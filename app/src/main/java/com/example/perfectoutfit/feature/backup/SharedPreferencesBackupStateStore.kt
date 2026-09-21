package com.example.perfectoutfit.feature.backup

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [BackupStateStore] backed by SharedPreferences, so the worker and the UI read it synchronously. */
@Singleton
class SharedPreferencesBackupStateStore @Inject constructor(
    @ApplicationContext context: Context
) : BackupStateStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var lastBackupTime: Long?
        get() = if (prefs.contains(KEY_TIME)) prefs.getLong(KEY_TIME, 0L) else null
        set(value) = prefs.edit {
            if (value == null) remove(KEY_TIME) else putLong(KEY_TIME, value)
        }

    override var lastHash: String?
        get() = prefs.getString(KEY_HASH, null)
        set(value) = prefs.edit {
            if (value == null) remove(KEY_HASH) else putString(KEY_HASH, value)
        }

    private companion object {
        const val PREFS_NAME = "drive_backup_state"
        const val KEY_TIME = "last_backup_time"
        const val KEY_HASH = "last_hash"
    }
}
