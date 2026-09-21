package com.example.perfectoutfit.feature.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class DriveApiException(val code: Int, message: String) : Exception("Drive API $code: $message")

/** [DriveClient] over the Drive v3 REST API, limited to files this app created (`drive.file`). */
class DriveRestClient(
    private val http: OkHttpClient,
    private val tokens: DriveTokenProvider,
    baseUrl: String = "https://www.googleapis.com/"
) : DriveClient {
    private val filesUrl = "${baseUrl}drive/v3/files".toHttpUrl()
    private val uploadUrl = "${baseUrl}upload/drive/v3/files".toHttpUrl()

    override suspend fun findOrCreateFolder(name: String): String {
        val query = "name = '${escape(name)}' and mimeType = '$FOLDER_MIME' and trashed = false"
        val found = getJson(
            filesUrl.newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "files(id)")
                .build()
        )
        (found["files"] as? JsonArray)?.firstOrNull()?.let { return it.jsonObject.string("id") }

        val body = buildJsonObject {
            put("name", name)
            put("mimeType", FOLDER_MIME)
        }.toString().toRequestBody(JSON)
        val url = filesUrl.newBuilder().addQueryParameter("fields", "id").build()
        return execute(Request.Builder().url(url).post(body)) { parseObject(it) }.string("id")
    }

    override suspend fun uploadFile(folderId: String, name: String, bytes: ByteArray, mime: String): String {
        val metadata = buildJsonObject {
            put("name", name)
            put("parents", JsonArray(listOf(JsonPrimitive(folderId))))
        }.toString()
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(JSON))
            .addPart(bytes.toRequestBody(mime.toMediaType()))
            .build()
        val url = uploadUrl.newBuilder()
            .addQueryParameter("uploadType", "multipart")
            .addQueryParameter("fields", "id")
            .build()
        return execute(Request.Builder().url(url).post(body)) { parseObject(it) }.string("id")
    }

    override suspend fun listFiles(folderId: String): List<DriveFile> {
        val files = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = filesUrl.newBuilder()
                .addQueryParameter("q", "'${escape(folderId)}' in parents and trashed = false")
                .addQueryParameter("fields", "nextPageToken,files(id,name)")
                .addQueryParameter("pageSize", "1000")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = getJson(url)
            (page["files"] as? JsonArray)?.forEach {
                val file = it.jsonObject
                files += DriveFile(file.string("id"), file.string("name"))
            }
            pageToken = page["nextPageToken"]?.jsonPrimitive?.content
        } while (pageToken != null)
        return files
    }

    override suspend fun downloadFile(id: String): ByteArray {
        val url = filesUrl.newBuilder().addPathSegment(id).addQueryParameter("alt", "media").build()
        return execute(Request.Builder().url(url).get()) { it.body!!.bytes() }
    }

    override suspend fun deleteFile(id: String) {
        val url = filesUrl.newBuilder().addPathSegment(id).build()
        execute(Request.Builder().url(url).delete(), tolerate = setOf(HTTP_NOT_FOUND)) { }
    }

    private suspend fun getJson(url: HttpUrl): JsonObject =
        execute(Request.Builder().url(url).get()) { parseObject(it) }

    private suspend fun <T> execute(
        builder: Request.Builder,
        tolerate: Set<Int> = emptySet(),
        read: (Response) -> T
    ): T {
        val request = builder.header("Authorization", "Bearer ${tokens.accessToken()}").build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code !in tolerate) {
                    throw DriveApiException(response.code, response.body?.string().orEmpty().take(ERROR_BODY_LIMIT))
                }
                read(response)
            }
        }
    }

    private fun parseObject(response: Response): JsonObject =
        Json.parseToJsonElement(response.body!!.string()).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    /** Escapes a value for use inside a single-quoted Drive query string. */
    private fun escape(value: String) = value.replace("\\", "\\\\").replace("'", "\\'")

    private companion object {
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        const val ERROR_BODY_LIMIT = 500
        const val HTTP_NOT_FOUND = 404
        val JSON = "application/json; charset=UTF-8".toMediaType()
    }
}
