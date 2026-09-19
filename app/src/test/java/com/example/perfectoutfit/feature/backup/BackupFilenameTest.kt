package com.example.perfectoutfit.feature.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BackupFilenameTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val millis = LocalDateTime.of(2026, 7, 25, 9, 43).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `formats timestamp with expected suffix`() {
        assertEquals("260725-0943 PerfectOutfit.json", BackupFilename.forTimestamp(millis, zone))
    }

    @Test
    fun `parses its own formatted filename`() {
        assertEquals(millis, BackupFilename.parseTimestamp(BackupFilename.forTimestamp(millis, zone), zone))
    }

    @Test
    fun `rejects unrelated filenames`() {
        assertNull(BackupFilename.parseTimestamp("readme.txt", zone))
        assertNull(BackupFilename.parseTimestamp("PerfectOutfit-backup.json", zone))
    }

    @Test
    fun `rejects malformed or impossible timestamps`() {
        assertNull(BackupFilename.parseTimestamp("not-a-date PerfectOutfit.json", zone))
        assertNull(BackupFilename.parseTimestamp("261399-0943 PerfectOutfit.json", zone))
        assertNull(BackupFilename.parseTimestamp("260725-0943x PerfectOutfit.json", zone))
    }
}
