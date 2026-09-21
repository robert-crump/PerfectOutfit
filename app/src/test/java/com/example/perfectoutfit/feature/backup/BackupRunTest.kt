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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock

class BackupRunTest {
    private val drive = FlakyDriveClient()
    private val notifier = FakeBackupFailureNotifier()
    private val service = DriveBackupService(
        drive,
        ExportImportManager(
            FakeClothingItemDao(), FakeWeatherSnapshotDao(), FakeOutfitEntryDao(), FakeOutfitItemDao(),
            FakeDatabaseTransactionRunner(), Json { ignoreUnknownKeys = true }
        ),
        FakeBackupStateStore(),
        Clock.systemUTC()
    )
    private val run = BackupRun(service, notifier)

    @Test
    fun `a successful backup reports success without a notification`() = runTest {
        assertTrue(run.run())

        assertFalse(notifier.visible)
        assertEquals(0, notifier.shown)
    }

    @Test
    fun `a failed backup posts the failure notification`() = runTest {
        drive.failing = true

        assertFalse(run.run())

        assertTrue(notifier.visible)
    }

    @Test
    fun `the failure notification clears after the next success`() = runTest {
        drive.failing = true
        run.run()

        drive.failing = false
        assertTrue(run.run())

        assertFalse(notifier.visible)
    }
}
