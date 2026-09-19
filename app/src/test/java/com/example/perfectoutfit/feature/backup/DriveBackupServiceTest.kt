package com.example.perfectoutfit.feature.backup

import com.example.perfectoutfit.core.model.BodyPart
import com.example.perfectoutfit.core.model.ClothingItem
import com.example.perfectoutfit.core.model.Sport
import com.example.perfectoutfit.feature.settings.ExportImportManager
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DriveBackupServiceTest {

    private class MutableClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now
        fun advance(d: Duration) {
            now = now.plus(d)
        }
    }

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val clothingDao = FakeClothingItemDao()
    private val exportImport = ExportImportManager(
        clothingDao, FakeWeatherSnapshotDao(), FakeOutfitEntryDao(), FakeOutfitItemDao(),
        FakeDatabaseTransactionRunner(), json
    )
    private val drive = FakeDriveClient()
    private val state = FakeBackupStateStore()
    private val clock = MutableClock(Instant.parse("2026-09-01T08:00:00Z"))
    private val service = DriveBackupService(drive, exportImport, state, clock)

    private fun item(id: Long) =
        ClothingItem(id = id, sport = Sport.CYCLING, bodyPart = BodyPart.HEAD_THROAT, name = "Item $id", isDefault = false)

    private suspend fun addItem(id: Long) = clothingDao.insertAll(listOf(item(id)))

    @Test
    fun `first backup uploads and records hash and time`() = runTest {
        addItem(1)

        assertEquals(BackupOutcome.UPLOADED, service.backup())

        assertEquals(listOf("260901-0800 PerfectOutfit.json"), drive.fileNames())
        assertNotNull(state.lastHash)
        assertEquals(clock.millis(), state.lastBackupTime)
    }

    @Test
    fun `unchanged export is skipped`() = runTest {
        addItem(1)
        service.backup()
        clock.advance(Duration.ofDays(1))

        assertEquals(BackupOutcome.SKIPPED, service.backup())

        assertEquals(1, drive.uploadCount)
    }

    @Test
    fun `changed export uploads and prunes to nine`() = runTest {
        repeat(12) { i ->
            addItem(i + 1L)
            clock.advance(Duration.ofDays(1))
            assertEquals(BackupOutcome.UPLOADED, service.backup())
        }

        assertEquals(12, drive.uploadCount)
        assertEquals(9, drive.fileNames().size)
        assertEquals(3, drive.deletedIds.size)
        assertEquals(9, service.listSnapshots().size)
    }

    @Test
    fun `missing folder re-uploads even when hash matches`() = runTest {
        addItem(1)
        service.backup()
        drive.deleteFolder(DriveBackupService.FOLDER_NAME)

        assertEquals(BackupOutcome.UPLOADED, service.backup())

        assertEquals(2, drive.uploadCount)
        assertEquals(1, drive.fileNames().size)
    }

    @Test
    fun `empty folder re-uploads even when hash matches`() = runTest {
        addItem(1)
        service.backup()
        val folder = drive.findOrCreateFolder(DriveBackupService.FOLDER_NAME)
        drive.listFiles(folder).forEach { drive.deleteFile(it.id) }

        assertEquals(BackupOutcome.UPLOADED, service.backup())
    }

    @Test
    fun `unrelated files do not count as backups`() = runTest {
        addItem(1)
        service.backup()
        val folder = drive.findOrCreateFolder(DriveBackupService.FOLDER_NAME)
        drive.listFiles(folder).forEach { drive.deleteFile(it.id) }
        drive.uploadFile(folder, "notes.txt", ByteArray(0), "text/plain")

        assertEquals(BackupOutcome.UPLOADED, service.backup())

        assertTrue(drive.fileNames().contains("notes.txt"))
    }

    @Test
    fun `onDisconnect clears state and leaves Drive files`() = runTest {
        addItem(1)
        service.backup()

        service.onDisconnect()

        assertNull(state.lastHash)
        assertNull(state.lastBackupTime)
        assertEquals(1, drive.fileNames().size)
        assertTrue(drive.deletedIds.isEmpty())
    }

    @Test
    fun `backup after disconnect uploads again`() = runTest {
        addItem(1)
        service.backup()
        service.onDisconnect()

        assertEquals(BackupOutcome.UPLOADED, service.backup())
    }

    @Test
    fun `listSnapshots is newest first`() = runTest {
        for (i in 1L..3L) {
            addItem(i)
            clock.advance(Duration.ofDays(1))
            service.backup()
        }

        val stamps = service.listSnapshots().map { it.timestamp }

        assertEquals(3, stamps.size)
        assertEquals(stamps.sortedDescending(), stamps)
    }

    @Test
    fun `restore round-trips a snapshot through import`() = runTest {
        addItem(1)
        addItem(2)
        service.backup()
        val snapshot = service.listSnapshots().single()
        clothingDao.deleteAll()
        addItem(99)

        service.restore(snapshot.id)

        assertEquals(listOf(item(1), item(2)), clothingDao.getAll())
    }
}
