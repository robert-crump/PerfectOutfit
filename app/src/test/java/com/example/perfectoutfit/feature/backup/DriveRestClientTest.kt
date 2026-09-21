package com.example.perfectoutfit.feature.backup

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveRestClientTest {
    private val server = MockWebServer()
    private lateinit var client: DriveRestClient

    @Before
    fun setUp() {
        server.start()
        client = DriveRestClient(OkHttpClient(), { "tok" }, server.url("/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setBody(body)

    @Test
    fun `findOrCreateFolder returns an existing folder without creating one`() = runBlocking {
        server.enqueue(json("""{"files":[{"id":"f1"}]}"""))

        assertEquals("f1", client.findOrCreateFolder("PerfectOutfit Backups"))

        val request = server.takeRequest()
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        assertEquals(
            "name = 'PerfectOutfit Backups' and mimeType = 'application/vnd.google-apps.folder' and trashed = false",
            request.requestUrl!!.queryParameter("q")
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `findOrCreateFolder creates the folder when none exists`() = runBlocking {
        server.enqueue(json("""{"files":[]}"""))
        server.enqueue(json("""{"id":"new"}"""))

        assertEquals("new", client.findOrCreateFolder("Backups"))

        server.takeRequest()
        val create = server.takeRequest()
        assertEquals("POST", create.method)
        assertTrue(create.body.readUtf8().contains(""""name":"Backups""""))
    }

    @Test
    fun `findOrCreateFolder escapes quotes in the query`() = runBlocking {
        server.enqueue(json("""{"files":[{"id":"f"}]}"""))

        client.findOrCreateFolder("It's")

        assertTrue(server.takeRequest().requestUrl!!.queryParameter("q")!!.startsWith("name = 'It\\'s'"))
    }

    @Test
    fun `uploadFile sends metadata with parent and the content in one multipart request`() = runBlocking {
        server.enqueue(json("""{"id":"file1"}"""))

        val id = client.uploadFile("folder", "a.json", "{\"x\":1}".toByteArray(), "application/json")

        assertEquals("file1", id)
        val request = server.takeRequest()
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/related"))
        assertEquals("multipart", request.requestUrl!!.queryParameter("uploadType"))
        val body = request.body.readUtf8()
        assertTrue(body.contains(""""parents":["folder"]"""))
        assertTrue(body.contains(""""name":"a.json""""))
        assertTrue(body.contains("{\"x\":1}"))
    }

    @Test
    fun `listFiles follows page tokens`() = runBlocking {
        server.enqueue(json("""{"nextPageToken":"p2","files":[{"id":"1","name":"a"}]}"""))
        server.enqueue(json("""{"files":[{"id":"2","name":"b"}]}"""))

        val files = client.listFiles("folder")

        assertEquals(listOf(DriveFile("1", "a"), DriveFile("2", "b")), files)
        assertEquals("'folder' in parents and trashed = false", server.takeRequest().requestUrl!!.queryParameter("q"))
        assertEquals("p2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
    }

    @Test
    fun `downloadFile returns the raw bytes`() = runBlocking {
        server.enqueue(MockResponse().setBody("hello"))

        assertArrayEquals("hello".toByteArray(), client.downloadFile("abc"))

        val url = server.takeRequest().requestUrl!!
        assertEquals("/drive/v3/files/abc", url.encodedPath)
        assertEquals("media", url.queryParameter("alt"))
    }

    @Test
    fun `deleteFile issues a DELETE and tolerates an already-deleted file`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(404))

        client.deleteFile("abc")
        client.deleteFile("gone")

        assertEquals("DELETE", server.takeRequest().method)
    }

    @Test
    fun `an error response throws DriveApiException with the status code`() {
        server.enqueue(json("""{"error":"nope"}""", code = 401))

        val error = assertThrows(DriveApiException::class.java) {
            runBlocking { client.listFiles("folder") }
        }

        assertEquals(401, error.code)
    }
}
