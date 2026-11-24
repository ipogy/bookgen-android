package com.bookgen.app.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Stages SAF single files (`app_data.db` / `.adoc` / `.md`) or SAF folder trees (`books/`)
 * into an isolated app cache directory so the Rust SQLite/FS legacy importer can inspect
 * standard local filesystem paths on Android 11+ scoped storage devices.
 */
object LegacySafImporter {
    private val ALLOWED_EXTENSIONS = setOf("db", "sqlite", "sqlite3", "json", "adoc", "md", "markdown")

    suspend fun stageSingleDocument(
        context: Context,
        documentUri: Uri,
    ): File = withContext(Dispatchers.IO) {
        val stagingRoot = createStagingDir(context)
        val rawName = queryDisplayName(context, documentUri) ?: "imported_file.db"
        val safeName = sanitizeSegment(rawName)
        val outFile = File(stagingRoot, safeName)
        copyUriToFile(context, documentUri, outFile)
        stagingRoot
    }

    suspend fun stageDocumentTree(
        context: Context,
        treeUri: Uri,
    ): File = withContext(Dispatchers.IO) {
        val stagingRoot = createStagingDir(context)
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        copyTreeRecursive(
            context = context,
            treeUri = treeUri,
            parentDocId = rootDocId,
            targetDir = stagingRoot,
            depth = 0,
        )
        stagingRoot
    }

    fun cleanupStagingDir(dir: File) {
        runCatching { dir.deleteRecursively() }
    }

    private fun copyTreeRecursive(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        targetDir: File,
        depth: Int,
    ) {
        if (depth > 3) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val docId = if (idIdx >= 0) cursor.getString(idIdx) else continue
                val displayName = if (nameIdx >= 0) cursor.getString(nameIdx) else continue
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else ""
                val safeName = sanitizeSegment(displayName)

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val subDir = File(targetDir, safeName).apply { mkdirs() }
                    copyTreeRecursive(context, treeUri, docId, subDir, depth + 1)
                } else {
                    val ext = safeName.substringAfterLast('.', "").lowercase()
                    if (ext in ALLOWED_EXTENSIONS) {
                        val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        copyUriToFile(context, fileUri, File(targetDir, safeName))
                    }
                }
            }
        }
    }

    private fun copyUriToFile(context: Context, sourceUri: Uri, destFile: File) {
        destFile.parentFile?.mkdirs()
        val input = context.contentResolver.openInputStream(sourceUri)
            ?: throw IllegalStateException("選択したファイルを読み込めません: $sourceUri")
        input.use { stream ->
            destFile.outputStream().use { out ->
                stream.copyTo(out)
            }
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
        return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else {
                null
            }
        } ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun createStagingDir(context: Context): File =
        File(context.cacheDir, "legacy_import_staging/${UUID.randomUUID()}").apply { mkdirs() }

    private fun sanitizeSegment(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._\\u3040-\\u9FFF-]"), "_")
            .trim('.')
            .ifEmpty { "item" }
}
