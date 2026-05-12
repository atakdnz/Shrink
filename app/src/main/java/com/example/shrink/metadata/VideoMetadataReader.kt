package com.example.shrink.metadata

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.shrink.compression.VideoInfo

class VideoMetadataReader(private val context: Context) {
    fun read(uri: Uri): VideoInfo {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(resolver, uri) ?: "video.mp4"
        val size = querySize(resolver, uri)
        val retriever = MediaMetadataRetriever()
        var duration: Long? = null
        var width: Int? = null
        var height: Int? = null
        var rotation: Int? = null
        var isHdr: Boolean? = null
        try {
            retriever.setDataSource(context, uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
            isHdr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                ?.toIntOrNull()
                ?.let { it == MediaFormat.COLOR_TRANSFER_ST2084 || it == MediaFormat.COLOR_TRANSFER_HLG }
        } catch (_: Exception) {
            // Missing metadata should not block compression.
        } finally {
            retriever.release()
        }
        val trackInfo = readTrackInfo(uri)
        return VideoInfo(
            uri = uri,
            displayName = displayName,
            sizeBytes = size,
            durationMs = duration,
            width = width,
            height = height,
            fps = trackInfo.fps,
            videoCodec = trackInfo.videoCodec,
            audioCodec = trackInfo.audioCodec,
            hasAudio = trackInfo.hasAudio,
            isHdr = isHdr,
            rotationDegrees = rotation
        )
    }

    fun canOpenVideo(uri: Uri): Boolean {
        val type = context.contentResolver.getType(uri)
        if (type != null && !type.startsWith("video/")) return false
        return try {
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }

    private fun readTrackInfo(uri: Uri): TrackInfo {
        val extractor = MediaExtractor()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                extractor.setDataSource(it.fileDescriptor)
                var fps: Float? = null
                var videoCodec: String? = null
                var audioCodec: String? = null
                var hasAudio = false
                repeat(extractor.trackCount) { index ->
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("video/") == true) {
                        videoCodec = mime
                        if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                            fps = format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                        }
                    } else if (mime?.startsWith("audio/") == true) {
                        hasAudio = true
                        audioCodec = mime
                    }
                }
                TrackInfo(fps, videoCodec, audioCodec, hasAudio)
            } ?: TrackInfo()
        } catch (_: Exception) {
            TrackInfo()
        } finally {
            extractor.release()
        }
    }

    private data class TrackInfo(
        val fps: Float? = null,
        val videoCodec: String? = null,
        val audioCodec: String? = null,
        val hasAudio: Boolean = false
    )
}
