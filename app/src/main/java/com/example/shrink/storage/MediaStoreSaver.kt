package com.example.shrink.storage

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File

class MediaStoreSaver(private val context: Context) {
    fun save(file: File): Result<Unit> {
        val resolver = context.contentResolver
        var uri: android.net.Uri? = null
        return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
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
            resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        }
        }.onFailure {
            uri?.let { resolver.delete(it, null, null) }
        }
    }
}
