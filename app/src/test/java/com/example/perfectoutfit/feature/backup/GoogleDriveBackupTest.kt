package com.example.perfectoutfit.feature.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.perfectoutfit.core.exportimport.ExportImportManager
import com.example.perfectoutfit.testutil.FakeClothingItemDao
import com.example.perfectoutfit.testutil.FakeOutfitEntryDao
import com.example.perfectoutfit.testutil.FakeOutfitItemDao
import com.example.perfectoutfit.testutil.FakeWeatherSnapshotDao
import com.example.perfectoutfit.testutil.outfitLoggingOver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

// SDK 35, not 34: RateOutfitActionReceiverTest leaves Room work running in the SDK 34 sandbox,
// which fails whichever runTest starts next there.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoogleDriveBackupTest {

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

    private val activity = Robolectric.buildActivity(Activity::class.java).get()
    private val consentIntent = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE
    )
    private val drive = FlakyDriveClient()
    private val state = FakeBackupStateStore()
    private val session = FakeDriveSession()
    private val scheduler = FakeScheduler()
    private val notifier = FakeBackupFailureNotifier()
    private val service = DriveBackupService(
        drive,
        ExportImportManager(
            FakeClothingItemDao(), FakeWeatherSnapshotDao(), FakeOutfitEntryDao(), FakeOutfitItemDao(),
            outfitLoggingOver(), Json { ignoreUnknownKeys = true }
        ),
        state,
        notifier,
        TickingClock()
    )
    private val backup = GoogleDriveBackup(session, scheduler, service, state)

    private fun ConnectResult.firstBackup(): Result<BackupOutcome> =
        (this as ConnectResult.Connected).firstBackup

    @Test
    fun `connecting marks the account, schedules the daily work and backs up immediately`() = runTest {
        val result = backup.connect(activity)

        assertEquals("me@example.com", (result as ConnectResult.Connected).email)
        assertEquals("me@example.com", session.connectedAccount.value)
        assertTrue(scheduler.scheduled)
        assertEquals(BackupOutcome.UPLOADED, result.firstBackup.getOrThrow())
        assertEquals(1, drive.uploadCount)
    }

    @Test
    fun `a cancelled sign-in connects nothing`() = runTest {
        session.chosenAccount = null

        assertEquals(ConnectResult.Cancelled, backup.connect(activity))

        assertNull(session.connectedAccount.value)
        assertFalse(scheduler.scheduled)
    }

    @Test
    fun `when consent is needed, the connection waits for it`() = runTest {
        session.authorization = DriveAuthorization.ResolutionRequired(consentIntent)

        assertEquals(ConnectResult.NeedsConsent(consentIntent), backup.connect(activity))

        assertNull(session.connectedAccount.value)
        assertFalse(scheduler.scheduled)
    }

    @Test
    fun `granted consent finishes connecting the signed-in account`() = runTest {
        session.authorization = DriveAuthorization.ResolutionRequired(consentIntent)
        backup.connect(activity)
        val data = Intent()

        val result = backup.completeConsent(data)

        assertEquals(BackupOutcome.UPLOADED, result.firstBackup().getOrThrow())
        assertEquals(listOf(data), session.completedConsents)
        assertEquals("me@example.com", session.connectedAccount.value)
        assertTrue(scheduler.scheduled)
    }

    @Test
    fun `consent with nothing waiting for it connects nothing`() = runTest {
        assertEquals(ConnectResult.Cancelled, backup.completeConsent(Intent()))

        assertNull(session.connectedAccount.value)
    }

    @Test
    fun `a consent that fails to authorize leaves the account disconnected`() = runTest {
        session.authorization = DriveAuthorization.ResolutionRequired(consentIntent)
        session.consentError = DriveReconnectRequiredException()
        backup.connect(activity)

        try {
            backup.completeConsent(Intent())
            fail("Expected the consent failure to be thrown")
        } catch (_: DriveReconnectRequiredException) {
        }

        assertNull(session.connectedAccount.value)
        assertFalse(scheduler.scheduled)
    }

    @Test
    fun `reconnecting produces a fresh backup even when the data is unchanged`() = runTest {
        backup.connect(activity)
        backup.disconnect()
        session.chosenAccount = "other@example.com"

        val result = backup.connect(activity)

        assertEquals(BackupOutcome.UPLOADED, result.firstBackup().getOrThrow())
        assertEquals(2, drive.uploadCount)
    }

    @Test
    fun `connecting ignores stale backup state that would skip the first backup`() = runTest {
        service.backup()
        assertEquals(BackupOutcome.SKIPPED, service.backup())

        val result = backup.connect(activity)

        assertEquals(BackupOutcome.UPLOADED, result.firstBackup().getOrThrow())
    }

    @Test
    fun `a failed first backup leaves the connection in place`() = runTest {
        drive.failing = true

        val result = backup.connect(activity)

        assertTrue(result.firstBackup().isFailure)
        assertEquals("me@example.com", session.connectedAccount.value)
        assertTrue(scheduler.scheduled)
    }

    @Test
    fun `disconnecting cancels the work, clears local state and keeps the Drive files`() = runTest {
        backup.connect(activity)

        backup.disconnect()

        assertFalse(scheduler.scheduled)
        assertNull(session.connectedAccount.value)
        assertNull(state.lastHash)
        assertNull(state.lastBackupTime)
        assertEquals(1, drive.fileNames().size)
    }

    @Test
    fun `disconnecting clears a pending failure notification`() = runTest {
        notifier.showFailure()

        backup.disconnect()

        assertFalse(notifier.visible)
    }

    @Test
    fun `a failed backup now throws without posting a notification`() = runTest {
        drive.failing = true

        try {
            backup.backupNow()
            fail("Expected the backup failure to be thrown")
        } catch (_: DriveApiException) {
        }

        assertEquals(0, notifier.shown)
    }

    @Test
    fun `status follows the account and backups, including a scheduled failure`() = runTest {
        val seen = mutableListOf<BackupStatus>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { backup.status.toList(seen) }
        assertEquals(BackupStatus(), seen.last())

        backup.connect(activity)
        val backedUp = seen.last()
        assertEquals("me@example.com", backedUp.account)
        assertEquals(state.lastBackupTime, backedUp.lastBackupTime)

        // What the scheduled backup records when it gives up.
        val failure = BackupFailure(time = 1L, reason = "Network error")
        state.lastFailure = failure
        assertEquals(failure, seen.last().lastFailure)

        backup.disconnect()
        assertEquals(BackupStatus(), seen.last())
    }
}
