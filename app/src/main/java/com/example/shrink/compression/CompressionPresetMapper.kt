package com.example.shrink.compression

import androidx.media3.common.MimeTypes
import kotlin.math.roundToInt

object CompressionPresetMapper {
    fun mapToEncodingConfig(
        videoInfo: VideoInfo,
        settings: CompressionSettings,
        adjustments: VideoAdjustments = VideoAdjustments()
    ): EncodingConfig {
        val (sourceWidth, sourceHeight) = displayDimensions(videoInfo)
        val adjustedDimensions = adjustedDimensions(sourceWidth, sourceHeight, adjustments)
        val codecMultiplier = if (settings.codec == OutputCodec.H264_AVC) 1.45 else 1.0
        val targetLongSide = when (settings.preset) {
            CompressionPreset.HIGH -> 2160
            CompressionPreset.BALANCED -> 1080
            CompressionPreset.SMALL -> 720
            CompressionPreset.TINY -> 480
            CompressionPreset.CUSTOM -> resolutionLongSide(settings.resolution)
        }
        val dimensions = scaledDimensions(adjustedDimensions.first, adjustedDimensions.second, targetLongSide)
        val fps = when (settings.preset) {
            CompressionPreset.HIGH -> capFps(videoInfo.fps, 60)
            CompressionPreset.BALANCED, CompressionPreset.SMALL -> capFps(videoInfo.fps, 30)
            CompressionPreset.TINY -> capFps(videoInfo.fps, 24)
            CompressionPreset.CUSTOM -> fpsValue(settings.fpsMode)
        }
        val audioBitrate = settings.customAudioBitrate ?: if (settings.audioMode == AudioMode.REMOVE) null else 128_000
        val presetBitrate = bitrateFor(dimensions.second ?: sourceHeight, settings.preset, codecMultiplier)
        val adjustedDurationMs = adjustments.adjustedDurationMs(videoInfo.durationMs)
        val targetBitrate = targetSizeBitrate(adjustedDurationMs, settings.targetSizeBytes, audioBitrate, settings.audioMode)
        val requestedBitrate = settings.customVideoBitrate ?: targetBitrate ?: presetBitrate
        val warning = if (targetBitrate != null && targetBitrate < 350_000) {
            "Target size may be too small for this video. Output quality may be poor."
        } else null
        return EncodingConfig(
            outputWidth = dimensions.first,
            outputHeight = dimensions.second,
            videoMimeType = if (settings.codec == OutputCodec.H265_HEVC) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264,
            videoBitrate = requestedBitrate.coerceAtLeast(250_000),
            audioBitrate = audioBitrate,
            fps = fps,
            removeAudio = settings.audioMode == AudioMode.REMOVE,
            warning = warning
        )
    }

    fun estimate(
        videoInfo: VideoInfo,
        settings: CompressionSettings,
        adjustments: VideoAdjustments = VideoAdjustments()
    ): CompressionEstimate {
        val config = mapToEncodingConfig(videoInfo, settings, adjustments)
        val adjustedDurationMs = adjustments.adjustedDurationMs(videoInfo.durationMs)
        val adjustedDisplayDimensions = adjustedDimensions(
            displayDimensions(videoInfo).first,
            displayDimensions(videoInfo).second,
            adjustments
        )
        val estimatedSize = estimateOutputSizeBytes(adjustedDurationMs, config.videoBitrate, config.audioBitrate, config.removeAudio)
        val savings = estimatedSize?.let { outputSize ->
            videoInfo.sizeBytes?.takeIf { it > 0L }?.let { originalSize ->
                (1f - outputSize.toFloat() / originalSize.toFloat()) * 100f
            }
        }
        return CompressionEstimate(
            outputWidth = config.outputWidth ?: adjustedDisplayDimensions.first,
            outputHeight = config.outputHeight ?: adjustedDisplayDimensions.second,
            videoBitrate = config.videoBitrate,
            audioBitrate = config.audioBitrate,
            fps = config.fps,
            removeAudio = config.removeAudio,
            estimatedOutputSizeBytes = estimatedSize,
            estimatedSavingsPercent = savings,
            warning = config.warning
        )
    }

    private fun resolutionLongSide(resolution: OutputResolution) = when (resolution) {
        OutputResolution.ORIGINAL -> null
        OutputResolution.P1080 -> 1080
        OutputResolution.P720 -> 720
        OutputResolution.P480 -> 480
        OutputResolution.P360 -> 360
    }

    private fun displayDimensions(videoInfo: VideoInfo): Pair<Int?, Int?> {
        val width = videoInfo.width
        val height = videoInfo.height
        if (width == null || height == null) return width to height
        val rotation = videoInfo.rotationDegrees ?: 0
        return if (rotation == 90 || rotation == 270) height to width else width to height
    }

    private fun adjustedDimensions(width: Int?, height: Int?, adjustments: VideoAdjustments): Pair<Int?, Int?> {
        if (width == null || height == null) return width to height
        val crop = adjustments.crop
        val croppedWidth = crop?.let { (width * (1f - it.leftFraction - it.rightFraction)).roundToInt() } ?: width
        val croppedHeight = crop?.let { (height * (1f - it.topFraction - it.bottomFraction)).roundToInt() } ?: height
        return if (adjustments.rotationDegrees == 90 || adjustments.rotationDegrees == 270) {
            croppedHeight to croppedWidth
        } else {
            croppedWidth to croppedHeight
        }
    }

    private fun scaledDimensions(width: Int?, height: Int?, maxResolution: Int?): Pair<Int?, Int?> {
        if (width == null || height == null || maxResolution == null) return null to null
        val constrainedSide = if (width >= height) height else width
        if (constrainedSide <= maxResolution) return null to null
        val scale = maxResolution.toFloat() / constrainedSide
        val outWidth = ((width * scale).roundToInt() / 2) * 2
        val outHeight = ((height * scale).roundToInt() / 2) * 2
        return outWidth.coerceAtLeast(2) to outHeight.coerceAtLeast(2)
    }

    private fun capFps(sourceFps: Float?, cap: Int): Int? {
        val fps = sourceFps ?: return cap
        return if (fps > cap) cap else null
    }

    private fun fpsValue(mode: FpsMode) = when (mode) {
        FpsMode.ORIGINAL -> null
        FpsMode.FPS_60 -> 60
        FpsMode.FPS_30 -> 30
        FpsMode.FPS_24 -> 24
    }

    private fun bitrateFor(height: Int?, preset: CompressionPreset, multiplier: Double): Int {
        val base = when (preset) {
            CompressionPreset.HIGH -> if ((height ?: 1080) > 1080) 8_000_000 else 5_500_000
            CompressionPreset.BALANCED -> if ((height ?: 1080) > 720) 3_800_000 else 2_200_000
            CompressionPreset.SMALL -> 1_600_000
            CompressionPreset.TINY -> 850_000
            CompressionPreset.CUSTOM -> 3_000_000
        }
        return (base * multiplier).roundToInt()
    }

    private fun targetSizeBitrate(
        durationMs: Long?,
        targetSizeBytes: Long?,
        audioBitrate: Int?,
        audioMode: AudioMode
    ): Int? {
        if (durationMs == null || targetSizeBytes == null || durationMs <= 0) return null
        val total = (targetSizeBytes * 8.0 / (durationMs / 1000.0)).roundToInt()
        return if (audioMode == AudioMode.REMOVE) total else total - (audioBitrate ?: 128_000)
    }

    private fun estimateOutputSizeBytes(
        durationMs: Long?,
        videoBitrate: Int?,
        audioBitrate: Int?,
        removeAudio: Boolean
    ): Long? {
        if (durationMs == null || durationMs <= 0 || videoBitrate == null) return null
        val totalBitrate = videoBitrate + if (removeAudio) 0 else (audioBitrate ?: 0)
        return ((totalBitrate / 8.0) * (durationMs / 1000.0)).roundToInt().toLong()
    }
}
