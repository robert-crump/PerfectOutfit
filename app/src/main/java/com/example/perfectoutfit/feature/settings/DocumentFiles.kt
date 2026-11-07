package com.example.perfectoutfit.feature.settings

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reads and writes the documents the user picks for export and import, named by their
 * content URI string. Two adapters exist: [ContentResolverDocumentFiles] and a fake in
 * `app/src/test`, so Settings export/import runs on the plain JVM.
 */
interface DocumentFiles {
    suspend fun readText(uri: String): String
    suspend fun writeText(uri: String, text: String)
}

/** Opens the picked documents through the content resolver. */
class ContentResolverDocumentFiles @Inject constructor(
    @param:ApplicationContext private val context: Context
) : DocumentFiles {

    override suspend fun readText(uri: String): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.bufferedReader().readText() }
            ?: throw IllegalStateException("Could not read file")
    }

    override suspend fun writeText(uri: String, text: String) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(Uri.parse(uri))?.use { it.write(text.toByteArray()) }
            ?: throw IllegalStateException("Could not write file")
    }
}
