package com.example.perfectoutfit.feature.backup

/** A [FakeDriveClient] that can be switched into a failing state. */
class FlakyDriveClient(private val delegate: FakeDriveClient = FakeDriveClient()) : DriveClient by delegate {
    var failing = false

    val uploadCount: Int get() = delegate.uploadCount
    fun fileNames(): List<String> = delegate.fileNames()

    override suspend fun findOrCreateFolder(name: String): String {
        if (failing) throw DriveApiException(500, "boom")
        return delegate.findOrCreateFolder(name)
    }
}
