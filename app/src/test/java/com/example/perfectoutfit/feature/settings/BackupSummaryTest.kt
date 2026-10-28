package com.example.perfectoutfit.feature.settings

import com.example.perfectoutfit.feature.backup.BackupFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSummaryTest {
    private val format: (Long) -> String = { "t$it" }

    @Test
    fun `never backed up`() {
        assertEquals("Last backed up: never", backupSummary(null, null, format))
    }

    @Test
    fun `last success only`() {
        assertEquals("Last backed up: t5", backupSummary(5, null, format))
    }

    @Test
    fun `a recorded failure follows the last success`() {
        assertEquals(
            "Last backed up: t5\nAutomatic backup failed t9: Network error",
            backupSummary(5, BackupFailure(9, "Network error"), format)
        )
    }
}
