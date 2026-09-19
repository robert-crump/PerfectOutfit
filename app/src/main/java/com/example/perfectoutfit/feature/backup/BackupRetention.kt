package com.example.perfectoutfit.feature.backup

/**
 * Keeps the newest [DAILY_COUNT] backups plus one weekly and one monthly slot, which are
 * simply the next two newest: they fill naturally as backups age out of the daily window.
 */
object BackupRetention {
    const val DAILY_COUNT = 7
    const val WEEKLY_COUNT = 1
    const val MONTHLY_COUNT = 1
    const val TOTAL_COUNT = DAILY_COUNT + WEEKLY_COUNT + MONTHLY_COUNT

    /** The elements of [backups] to delete so at most [TOTAL_COUNT] (the newest) remain. */
    fun <T> selectForDeletion(backups: List<T>, timestampOf: (T) -> Long): List<T> {
        if (backups.size <= TOTAL_COUNT) return emptyList()
        return backups.sortedByDescending(timestampOf).drop(TOTAL_COUNT)
    }
}
