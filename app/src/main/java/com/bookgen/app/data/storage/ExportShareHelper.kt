package com.bookgen.app.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bookgen.app.domain.model.ExportFormatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ExportShareHelper {
    fun shareExportedFile(
        context: Context,
        filePath: String,
        format: ExportFormatType,
    ): Result<Unit> = runCatching {
        val file = File(filePath)
        require(file.exists() && file.isFile) { "書き出しファイルが見つかりません: $filePath" }
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "${format.label} を共有").apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    }

    suspend fun copyExportedFileToUri(
        context: Context,
        sourcePath: String,
        destinationUri: Uri,
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceFile = File(sourcePath)
            require(sourceFile.exists() && sourceFile.isFile) { "書き出し元ファイルが存在しません" }
            val out = context.contentResolver.openOutputStream(destinationUri)
                ?: throw IllegalStateException("保存先 URI を開けませんでした")
            out.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }
    }
}
