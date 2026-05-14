package com.example.shrink.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

class MediaStoreSaver(private val context: Context) {
    fun save(file: File, capturedAtMillis: Long? = null): Result<Unit> {
        val resolver = context.contentResolver
        var uri: android.net.Uri? = null
        return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            capturedAtMillis?.let {
                put(MediaStore.Video.Media.DATE_TAKEN, it)
                put(MediaStore.MediaColumns.DATE_MODIFIED, it / 1000L)
                put(MediaStore.MediaColumns.DATE_ADDED, it / 1000L)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoCompressor")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create MediaStore item")
        resolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Could not open MediaStore output")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }, null, null)
        }
        capturedAtMillis?.let {
            file.setLastModified(it)
            resolver.update(uri, dateValues(it), null, null)
        }
        Unit
        }.onFailure {
            uri?.let { resolver.delete(it, null, null) }
        }
    }

    private fun dateValues(capturedAtMillis: Long) = ContentValues().apply {
        put(MediaStore.Video.Media.DATE_TAKEN, capturedAtMillis)
        put(MediaStore.MediaColumns.DATE_MODIFIED, capturedAtMillis / 1000L)
        put(MediaStore.MediaColumns.DATE_ADDED, capturedAtMillis / 1000L)
    }
}
