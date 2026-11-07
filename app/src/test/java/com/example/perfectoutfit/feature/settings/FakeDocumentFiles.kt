package com.example.perfectoutfit.feature.settings

/** In-memory [DocumentFiles]: documents by URI string; reading a missing one fails like an unreadable file. */
class FakeDocumentFiles(vararg documents: Pair<String, String>) : DocumentFiles {
    val documents = mutableMapOf(*documents)

    override suspend fun readText(uri: String): String =
        documents[uri] ?: throw IllegalStateException("Could not read file")

    override suspend fun writeText(uri: String, text: String) {
        documents[uri] = text
    }
}
