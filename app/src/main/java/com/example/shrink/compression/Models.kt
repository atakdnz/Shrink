package com.example.shrink.compression

import android.net.Uri
import java.io.File

data class VideoInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val fps: Float?,
    val videoCodec: String?,
    val audioCodec: String?,
    val hasAudio: Boolean,
    val isHdr: Boolean?,
    val rotationDegrees: Int?
)

data class CompressionSettings(
    val preset: CompressionPreset = CompressionPreset.BALANCED,
    val resolution: OutputResolution = OutputResolution.ORIGINAL,
    val codec: OutputCodec = OutputCodec.H265_HEVC,
    val fpsMode: FpsMode = FpsMode.ORIGINAL,
    val audioMode: AudioMode = AudioMode.KEEP,
    val targetSizeBytes: Long? = null,
    val customVideoBitrate: Int? = null,
    val customAudioBitrate: Int? = null
) {
    companion object {
        fun default() = CompressionSettings()
    }
}

enum class CompressionPreset { HIGH, BALANCED, SMALL, TINY, CUSTOM }
enum class OutputCodec { H265_HEVC, H264_AVC }
enum class OutputResolution { ORIGINAL, P1080, P720, P480, P360 }
enum class FpsMode { ORIGINAL, FPS_60, FPS_30, FPS_24 }
enum class AudioMode { KEEP, REMOVE }

data class CompressionInput(val uri: Uri, val videoInfo: VideoInfo)
data class CompressionOutput(val tempFile: File, val finalFile: File)
data class CompressionProgress(val percent: Float, val message: String? = null)

sealed interface CompressionResult {
    data class Success(
        val outputFile: File,
        val outputSizeBytes: Long,
        val originalSizeBytes: Long?,
        val outputUri: Uri?
    ) : CompressionResult

    data class Failure(
        val reason: CompressionFailureReason,
        val message: String,
        val throwable: Throwable? = null
    ) : CompressionResult

    data object Cancelled : CompressionResult
}

enum class CompressionFailureReason {
    INPUT_URI_INVALID,
    INPUT_METADATA_FAILED,
    UNSUPPORTED_INPUT,
    DECODER_FAILED,
    ENCODER_FAILED,
    H265_UNAVAILABLE,
    STORAGE_INSUFFICIENT,
    OUTPUT_CREATION_FAILED,
    MEDIA3_EXPORT_FAILED,
    CANCELLED,
    OUTPUT_LARGER_THAN_INPUT,
    UNKNOWN
}

data class EncodingConfig(
    val outputWidth: Int?,
    val outputHeight: Int?,
    val videoMimeType: String,
    val videoBitrate: Int?,
    val audioBitrate: Int?,
    val fps: Int?,
    val removeAudio: Boolean,
    val warning: String? = null
)

data class CompressedVideo(
    val file: File,
    val contentUri: Uri,
    val originalSizeBytes: Long?,
    val outputSizeBytes: Long,
    val compressionRatioPercent: Float?
)

sealed interface CompressionJobState {
    data object Idle : CompressionJobState
    data object LoadingMetadata : CompressionJobState
    data object Preparing : CompressionJobState
    data class Running(val progress: Float) : CompressionJobState
    data object Cancelling : CompressionJobState
    data class Success(val result: CompressionResult.Success) : CompressionJobState
    data class Failed(val reason: CompressionFailureReason, val message: String) : CompressionJobState
    data object Cancelled : CompressionJobState
}

data class CompressorUiState(
    val selectedVideo: VideoInfo? = null,
    val settings: CompressionSettings = CompressionSettings.default(),
    val jobState: CompressionJobState = CompressionJobState.Idle,
    val output: CompressedVideo? = null,
    val launchedFromShareSheet: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val errorMessage: String? = null,
    val warningMessage: String? = null,
    val savedMessage: String? = null,
    val shouldAutoShare: Boolean = false
)
