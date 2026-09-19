package com.example.perfectoutfit.feature.backup

data class DriveFile(val id: String, val name: String)

/** The slice of Google Drive the backup service needs. The real REST client is a later slice. */
interface DriveClient {
    suspend fun findOrCreateFolder(name: String): String
    suspend fun uploadFile(folderId: String, name: String, bytes: ByteArray, mime: String): String
    suspend fun listFiles(folderId: String): List<DriveFile>
    suspend fun downloadFile(id: String): ByteArray
    suspend fun deleteFile(id: String)
}
