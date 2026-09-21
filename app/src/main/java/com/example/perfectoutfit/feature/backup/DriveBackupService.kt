package com.example.perfectoutfit.feature.backup

import com.example.perfectoutfit.feature.settings.ExportImportManager
import com.example.perfectoutfit.feature.settings.CURRENT_EXPORT_VERSION
import kotlinx.serialization.SerializationException
import java.security.MessageDigest
import java.time.Clock

/** Whether this app can restore a Drive snapshot. */
enum class SnapshotCompatibility { COMPATIBLE, NEWER_VERSION, UNREADABLE }

data class BackupSnapshot(
    val id: String,
    val timestamp: Long,
    val compatibility: SnapshotCompatibility = SnapshotCompatibility.COMPATIBLE
) {
    val restorable: Boolean get() = compatibility == SnapshotCompatibility.COMPATIBLE
}

enum class BackupOutcome { UPLOADED, SKIPPED }

/** Owns every Drive backup/restore decision; all I/O goes through [DriveClient]. */
class DriveBackupService(
    private val drive: DriveClient,
    private val exportImport: ExportImportManager,
    private val state: BackupStateStore,
    private val clock: Clock
) {
    /**
     * Uploads a snapshot unless the export is unchanged since the last backup *and* the Drive
     * folder still holds at least one backup (a deleted folder must be repopulated).
     */
    suspend fun backup(): BackupOutcome {
        val json = exportImport.exportToJson()
        val hash = sha256(json)
        val folderId = drive.findOrCreateFolder(FOLDER_NAME)

        if (hash == state.lastHash && backupsIn(folderId).isNotEmpty()) return BackupOutcome.SKIPPED

        val now = clock.millis()
        drive.uploadFile(
            folderId, BackupFilename.forTimestamp(now, clock.zone), json.toByteArray(Charsets.UTF_8), MIME
        )
        BackupRetention.selectForDeletion(backupsIn(folderId)) { it.timestamp }
            .forEach { drive.deleteFile(it.id) }

        state.lastHash = hash
        state.lastBackupTime = now
        return BackupOutcome.UPLOADED
    }

    /** Backups in the Drive folder, newest first, each marked with whether this app can restore it. */
    suspend fun listSnapshots(): List<BackupSnapshot> =
        backupsIn(drive.findOrCreateFolder(FOLDER_NAME))
            .sortedByDescending { it.timestamp }
            .map { it.copy(compatibility = compatibilityOf(it.id)) }

    suspend fun restore(snapshotId: String) {
        exportImport.importFromJson(drive.downloadFile(snapshotId).toString(Charsets.UTF_8))
    }

    /** Forgets local backup state; Drive files stay. */
    fun onDisconnect() = state.clear()

    private suspend fun compatibilityOf(snapshotId: String): SnapshotCompatibility = try {
        val version = exportImport.fileVersion(drive.downloadFile(snapshotId).toString(Charsets.UTF_8))
        if (version != null && version > CURRENT_EXPORT_VERSION) {
            SnapshotCompatibility.NEWER_VERSION
        } else {
            SnapshotCompatibility.COMPATIBLE
        }
    } catch (_: SerializationException) {
        SnapshotCompatibility.UNREADABLE
    }

    private suspend fun backupsIn(folderId: String): List<BackupSnapshot> =
        drive.listFiles(folderId).mapNotNull { file ->
            BackupFilename.parseTimestamp(file.name, clock.zone)?.let { BackupSnapshot(file.id, it) }
        }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    companion object {
        const val FOLDER_NAME = "PerfectOutfit Backups"
        private const val MIME = "application/json"
    }
}
