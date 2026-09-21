package com.example.perfectoutfit.feature.backup

import com.example.perfectoutfit.feature.settings.ExportImportManager
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class DriveConnectionTest {

    private class FakeSession : DriveSession {
        var account: String? = null
        override fun markConnected(accountEmail: String) {
            account = accountEmail
        }
        override suspend fun disconnect() {
            account = null
        }
    }

    private class FakeScheduler : BackupScheduler {
        var scheduled = false
        override fun schedule() {
            scheduled = true
        }
        override fun cancel() {
            scheduled = false
        }
    }

    /** Advances a minute per reading so back-to-back backups get distinct file names. */
    private class TickingClock : Clock() {
        private var now = Instant.parse("2026-09-01T08:00:00Z")
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now.also { now = now.plus(Duration.ofMinutes(1)) }
    }

    private val drive = FlakyDriveClient()
    private val state = FakeBackupStateStore()
    private val session = FakeSession()
    private val scheduler = FakeScheduler()
    private val notifier = FakeBackupFailureNotifier()
    private val service = DriveBackupService(
        drive,
        ExportImportManager(
            FakeClothingItemDao(), FakeWeatherSnapshotDao(), FakeOutfitEntryDao(), FakeOutfitItemDao(),
            FakeDatabaseTransactionRunner(), Json { ignoreUnknownKeys = true }
        ),
        state,
        TickingClock()
    )
    private val connection = DriveConnection(session, scheduler, service, notifier)

    @Test
    fun `connecting marks the account, schedules the daily work and backs up immediately`() = runTest {
        val result = connection.completeConnect("me@example.com")

        assertEquals("me@example.com", session.account)
        assertTrue(scheduler.scheduled)
        assertEquals(BackupOutcome.UPLOADED, result.getOrThrow())
        assertEquals(1, drive.uploadCount)
    }

    @Test
    fun `reconnecting produces a fresh backup even when the data is unchanged`() = runTest {
        connection.completeConnect("me@example.com")
        connection.disconnect()

        val result = connection.completeConnect("other@example.com")

        assertEquals(BackupOutcome.UPLOADED, result.getOrThrow())
        assertEquals(2, drive.uploadCount)
    }

    @Test
    fun `connecting ignores stale backup state that would skip the first backup`() = runTest {
        service.backup()
        assertEquals(BackupOutcome.SKIPPED, service.backup())

        val result = connection.completeConnect("me@example.com")

        assertEquals(BackupOutcome.UPLOADED, result.getOrThrow())
    }

    @Test
    fun `a failed first backup leaves the connection in place`() = runTest {
        drive.failing = true

        val result = connection.completeConnect("me@example.com")

        assertTrue(result.isFailure)
        assertEquals("me@example.com", session.account)
        assertTrue(scheduler.scheduled)
    }

    @Test
    fun `disconnecting cancels the work, clears local state and keeps the Drive files`() = runTest {
        connection.completeConnect("me@example.com")

        connection.disconnect()

        assertFalse(scheduler.scheduled)
        assertNull(session.account)
        assertNull(state.lastHash)
        assertNull(state.lastBackupTime)
        assertEquals(1, drive.fileNames().size)
    }

    @Test
    fun `disconnecting clears a pending failure notification`() = runTest {
        notifier.showFailure()

        connection.disconnect()

        assertFalse(notifier.visible)
    }

    @Test
    fun `a successful backup now clears the failure notification`() = runTest {
        notifier.showFailure()

        connection.backupNow()

        assertFalse(notifier.visible)
    }

    @Test
    fun `a failed backup now reports the failure without posting a notification`() = runTest {
        drive.failing = true

        val result = connection.backupNow()

        assertTrue(result.isFailure)
        assertEquals(0, notifier.shown)
    }
}
