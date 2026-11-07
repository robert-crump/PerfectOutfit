package com.example.perfectoutfit.feature.backup

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

// SDK 35, not 34: RateOutfitActionReceiverTest leaves Room work running in the SDK 34 sandbox,
// which fails whichever runTest starts next there.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveBackupViewModelTest {

    private val backup = FakeDriveBackup()
    private val activity = Robolectric.buildActivity(Activity::class.java).get()
    private val consentIntent = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(), 0, Intent(), PendingIntent.FLAG_IMMUTABLE
    )
    private val snapshot = BackupSnapshot(id = "file-1", timestamp = 1_790_000_000_000L)
    private lateinit var viewModel: DriveBackupViewModel

    private val state get() = viewModel.uiState.value

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = DriveBackupViewModel(backup)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun connected(firstBackup: Result<BackupOutcome>) =
        ConnectResult.Connected("me@example.com", firstBackup)

    @Test
    fun `connecting reports the account and the first backup`() {
        backup.connectResult = connected(Result.success(BackupOutcome.UPLOADED))

        viewModel.connect(activity)

        assertEquals("Connected as me@example.com. Backup uploaded", state.message)
        assertFalse(state.isBusy)
    }

    @Test
    fun `a failed first backup is reported with a readable reason`() {
        backup.connectResult = connected(Result.failure(DriveApiException(500, "{ long json }")))

        viewModel.connect(activity)

        assertEquals(
            "Connected as me@example.com, but the first backup failed: Google Drive returned error 500",
            state.message
        )
    }

    @Test
    fun `a cancelled sign-in shows nothing`() {
        backup.connectResult = ConnectResult.Cancelled

        viewModel.connect(activity)

        assertNull(state.message)
        assertNull(state.consentRequest)
        assertFalse(state.isBusy)
    }

    @Test
    fun `a sign-in failure is reported`() {
        backup.error = IOException("unreachable")

        viewModel.connect(activity)

        assertEquals("Connecting to Google Drive failed: Network error: unreachable", state.message)
    }

    @Test
    fun `granted consent finishes connecting`() {
        backup.connectResult = ConnectResult.NeedsConsent(consentIntent)
        viewModel.connect(activity)
        assertEquals(consentIntent, state.consentRequest)
        viewModel.consentLaunched()
        assertNull(state.consentRequest)

        backup.consentResult = connected(Result.success(BackupOutcome.SKIPPED))
        val data = Intent()
        viewModel.onConsentResult(Activity.RESULT_OK, data)

        assertEquals(listOf(data), backup.consents)
        assertEquals("Connected as me@example.com. Already backed up, nothing has changed", state.message)
    }

    @Test
    fun `refused consent is reported without finishing the connection`() {
        backup.connectResult = ConnectResult.NeedsConsent(consentIntent)
        viewModel.connect(activity)
        viewModel.consentLaunched()

        viewModel.onConsentResult(Activity.RESULT_CANCELED, null)

        assertTrue(backup.consents.isEmpty())
        assertEquals("Google Drive access was not granted", state.message)
    }

    @Test
    fun `backing up now reports uploaded and skipped`() {
        viewModel.backupNow()
        assertEquals("Backup uploaded", state.message)

        backup.backupOutcome = BackupOutcome.SKIPPED
        viewModel.backupNow()
        assertEquals("Already backed up, nothing has changed", state.message)
    }

    @Test
    fun `a failed backup now shows the short reason, not Drive's error body`() {
        backup.error = DriveApiException(403, "{ \"error\": { \"code\": 403, \"message\": \"long\" } }")

        viewModel.backupNow()

        assertEquals("Backup failed: Google Drive returned error 403", state.message)
        assertFalse(state.isBusy)
    }

    @Test
    fun `restoring a chosen snapshot after confirmation`() {
        backup.snapshots = listOf(snapshot)

        viewModel.openRestorePicker()
        assertEquals(listOf(snapshot), state.snapshots)
        viewModel.chooseSnapshot(snapshot)
        assertNull(state.snapshots)
        assertEquals(snapshot, state.restoreCandidate)
        viewModel.confirmRestore()

        assertEquals(listOf("file-1"), backup.restored)
        assertNull(state.restoreCandidate)
        assertEquals("Restored the backup from ${BackupTimeFormat.format(snapshot.timestamp)}", state.message)
    }

    @Test
    fun `an empty Drive folder has nothing to restore`() {
        viewModel.openRestorePicker()

        assertNull(state.snapshots)
        assertEquals("No backups found in Drive", state.message)
    }

    @Test
    fun `a snapshot this app cannot read cannot be chosen`() {
        val newer = snapshot.copy(compatibility = SnapshotCompatibility.NEWER_VERSION)
        backup.snapshots = listOf(newer)
        viewModel.openRestorePicker()

        viewModel.chooseSnapshot(newer)

        assertNull(state.restoreCandidate)
        assertEquals(listOf(newer), state.snapshots)
    }

    @Test
    fun `dismissing the confirmation restores nothing`() {
        backup.snapshots = listOf(snapshot)
        viewModel.openRestorePicker()
        viewModel.chooseSnapshot(snapshot)

        viewModel.dismissRestoreConfirmation()
        viewModel.confirmRestore()

        assertTrue(backup.restored.isEmpty())
    }

    @Test
    fun `a failed restore is reported`() {
        backup.snapshots = listOf(snapshot)
        viewModel.openRestorePicker()
        viewModel.chooseSnapshot(snapshot)
        backup.error = IOException()

        viewModel.confirmRestore()

        assertEquals("Restore failed: Network error", state.message)
    }

    @Test
    fun `a scheduled failure shows up without any action`() {
        val failure = BackupFailure(time = 5L, reason = "Google Drive returned error 500")

        backup.status.value = BackupStatus(account = "me@example.com", lastBackupTime = 3L, lastFailure = failure)

        assertEquals("me@example.com", state.account)
        assertEquals(3L, state.lastBackupTime)
        assertEquals(failure, state.lastFailure)
    }

    @Test
    fun `busy while an operation runs, idle after it fails`() {
        val gate = CompletableDeferred<Unit>()
        backup.gate = gate
        backup.error = IOException()

        viewModel.disconnect()
        assertTrue(state.isBusy)

        gate.complete(Unit)
        assertFalse(state.isBusy)
        assertEquals("Disconnect failed: Network error", state.message)
    }
}
