package com.example.perfectoutfit.feature.backup

/** In-memory [DriveClient]: folders and files live in maps, ids are sequential. */
class FakeDriveClient : DriveClient {
    private val folders = mutableMapOf<String, String>()
    private val files = linkedMapOf<String, Stored>()
    private var nextId = 1

    private class Stored(val folderId: String, val name: String, val bytes: ByteArray)

    var uploadCount = 0
        private set
    val deletedIds = mutableListOf<String>()

    override suspend fun findOrCreateFolder(name: String): String =
        folders.getOrPut(name) { "folder-${nextId++}" }

    override suspend fun uploadFile(folderId: String, name: String, bytes: ByteArray, mime: String): String {
        val id = "file-${nextId++}"
        files[id] = Stored(folderId, name, bytes)
        uploadCount++
        return id
    }

    override suspend fun listFiles(folderId: String): List<DriveFile> =
        files.filterValues { it.folderId == folderId }.map { (id, f) -> DriveFile(id, f.name) }

    override suspend fun downloadFile(id: String): ByteArray = files.getValue(id).bytes

    override suspend fun deleteFile(id: String) {
        files.remove(id)
        deletedIds += id
    }

    /** Simulates the user deleting the whole backup folder in Drive. */
    fun deleteFolder(name: String) {
        val id = folders.remove(name) ?: return
        files.values.removeAll { it.folderId == id }
    }

    fun fileNames(): List<String> = files.values.map { it.name }
}
