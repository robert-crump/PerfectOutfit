package com.example.perfectoutfit.feature.backup

import com.example.perfectoutfit.feature.settings.ExportImportManager
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeDatabaseTransactionRunner
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class BackupRunTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-20T03:00:00Z"), ZoneOffset.UTC)
    private val drive = FlakyDriveClient()
    private val notifier = FakeBackupFailureNotifier()
    private val state = FakeBackupStateStore()
    private val service = DriveBackupService(
        drive,
        ExportImportManager(
            FakeClothingItemDao(), FakeWeatherSnapshotDao(), FakeOutfitEntryDao(), FakeOutfitItemDao(),
            FakeDatabaseTransactionRunner(), Json { ignoreUnknownKeys = true }
        ),
        state,
        clock
    )
    private val run = BackupRun(service, notifier, state, clock)
    private val lastAttempt = BackupRun.MAX_ATTEMPTS - 1

    @Test
    fun `a successful backup reports success without a notification`() = runTest {
        assertEquals(BackupRunResult.Success, run.run())

        assertFalse(notifier.visible)
        assertEquals(0, notifier.shown)
    }

    @Test
    fun `a transient failure is retried silently`() = runTest {
        drive.failing = true

        val result = run.run(attempt = 0)

        assertTrue(result is BackupRunResult.Retry)
        assertEquals(0, notifier.shown)
        assertNull(state.lastFailure)
    }

    @Test
    fun `a transient failure on the last attempt posts the notification and records the reason`() = runTest {
        drive.failing = true

        val result = run.run(attempt = lastAttempt)

        assertTrue(result is BackupRunResult.Failed)
        assertTrue(notifier.visible)
        assertEquals(BackupFailure(clock.millis(), "Google Drive returned error 500"), state.lastFailure)
    }

    @Test
    fun `revoked access is reported on the first attempt`() = runTest {
        drive.failing = true
        drive.error = DriveReconnectRequiredException()

        val result = run.run(attempt = 0)

        assertTrue(result is BackupRunResult.Failed)
        assertTrue(notifier.visible)
        assertEquals("Drive access needs to be reconnected in Settings", state.lastFailure?.reason)
    }

    @Test
    fun `a Drive client error is reported on the first attempt`() = runTest {
        drive.failing = true
        drive.error = DriveApiException(403, "forbidden")

        assertTrue(run.run(attempt = 0) is BackupRunResult.Failed)
    }

    @Test
    fun `a timeout inside the backup is retried rather than treated as cancellation`() = runTest {
        drive.failing = true
        drive.error = timeoutException()

        assertTrue(run.run(attempt = 0) is BackupRunResult.Retry)
    }

    @Test
    fun `the notification and recorded failure clear after the next success`() = runTest {
        drive.failing = true
        run.run(attempt = lastAttempt)

        drive.failing = false
        assertEquals(BackupRunResult.Success, run.run())

        assertFalse(notifier.visible)
        assertNull(state.lastFailure)
    }

    @Test
    fun `a skipped backup also clears the recorded failure`() = runTest {
        run.run()
        drive.failing = true
        run.run(attempt = lastAttempt)

        drive.failing = false
        assertEquals(BackupOutcome.SKIPPED, service.backup())

        assertNull(state.lastFailure)
    }

    @Test
    fun `transient errors are server errors, throttling, timeouts, network and unknown errors`() {
        assertTrue(BackupRun.isTransient(DriveApiException(500, "")))
        assertTrue(BackupRun.isTransient(DriveApiException(503, "")))
        assertTrue(BackupRun.isTransient(DriveApiException(429, "")))
        assertTrue(BackupRun.isTransient(DriveApiException(408, "")))
        assertTrue(BackupRun.isTransient(IOException("reset")))
        assertTrue(BackupRun.isTransient(IllegalStateException("?")))
        assertFalse(BackupRun.isTransient(DriveApiException(401, "")))
        assertFalse(BackupRun.isTransient(DriveApiException(404, "")))
        assertFalse(BackupRun.isTransient(DriveReconnectRequiredException()))
    }

    @Test
    fun `reasons are short and readable`() {
        assertEquals("Google Drive returned error 503", BackupRun.reasonFor(DriveApiException(503, "{ long json }")))
        assertEquals("Network error: timeout", BackupRun.reasonFor(IOException("timeout")))
        assertEquals("Network error", BackupRun.reasonFor(IOException()))
    }

    private suspend fun timeoutException(): Exception = try {
        withTimeout(1) { delay(10) }
        error("unreachable")
    } catch (e: Exception) {
        e
    }
}
