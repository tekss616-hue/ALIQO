package com.aliqo.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedMedia(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

suspend fun readPickedMedia(context: Context, uri: Uri): PickedMedia = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var fileName = "upload.bin"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) fileName = cursor.getString(index)?.trim().orEmpty().ifBlank { fileName }
        }
    }
    fileName = fileName.replace(Regex("[\\u0000-\\u001f]"), "_").take(240).ifBlank { "upload.bin" }
    val mimeType = resolver.getType(uri)?.trim()?.lowercase().orEmpty().ifBlank { "application/octet-stream" }
    val bytes = resolver.openInputStream(uri)?.use { input ->
        val max = 100 * 1024 * 1024
        val buffer = ByteArray(64 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            require(total <= max) { "Media is larger than 100 MB" }
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    } ?: error("Could not read selected media")
    require(bytes.isNotEmpty()) { "Selected media is empty" }
    PickedMedia(fileName, mimeType, bytes)
}
