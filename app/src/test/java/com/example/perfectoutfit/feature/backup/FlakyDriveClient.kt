package com.example.perfectoutfit.feature.backup

/** A [FakeDriveClient] that can be switched into a failing state. */
class FlakyDriveClient(private val delegate: FakeDriveClient = FakeDriveClient()) : DriveClient by delegate {
    var failing = false

    /** What a failing call throws; a Drive server error unless a test needs another. */
    var error: Exception = DriveApiException(500, "boom")

    val uploadCount: Int get() = delegate.uploadCount
    fun fileNames(): List<String> = delegate.fileNames()

    override suspend fun findOrCreateFolder(name: String): String {
        if (failing) throw error
        return delegate.findOrCreateFolder(name)
    }
}
