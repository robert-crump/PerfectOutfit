package com.example.perfectoutfit.feature.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionTest {
    private fun days(from: Long, to: Long) = (from..to).toList()
    private fun select(list: List<Long>) = BackupRetention.selectForDeletion(list) { it }

    @Test
    fun `keeps everything up to the cap`() {
        assertTrue(select(days(1, 5)).isEmpty())
        assertTrue(select(days(1, 9)).isEmpty())
    }

    @Test
    fun `deletes only the oldest beyond the cap`() {
        assertEquals(listOf(1L), select(days(1, 10)))
    }

    @Test
    fun `deletes all but the nine newest regardless of input order`() {
        assertEquals(days(1, 6).toSet(), select(days(1, 15).shuffled()).toSet())
    }

    @Test
    fun `converges to nine over many simulated days`() {
        var backups = emptyList<Long>()
        for (day in 1L..60L) {
            backups = backups + day
            backups = backups - select(backups).toSet()
            assertTrue(backups.size <= BackupRetention.TOTAL_COUNT)
        }
        assertEquals(days(52, 60), backups.sorted())
    }
}
