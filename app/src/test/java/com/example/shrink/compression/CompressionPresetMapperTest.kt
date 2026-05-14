package com.example.shrink.compression

import android.net.Uri
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class CompressionPresetMapperTest {
    @Test
    fun balancedCapsLargeVideoTo1080pAnd30Fps() {
        val config = CompressionPresetMapper.mapToEncodingConfig(
            videoInfo = videoInfo(width = 3840, height = 2160, fps = 60f),
            settings = CompressionSettings.default()
        )

        assertEquals(1920, config.outputWidth)
        assertEquals(1080, config.outputHeight)
        assertEquals(30, config.fps)
        assertEquals(MimeTypes.VIDEO_H265, config.videoMimeType)
        assertNotNull(config.videoBitrate)
    }

    @Test
    fun h264UsesCompatibilityMimeTypeAndHigherBitrate() {
        val h265 = CompressionPresetMapper.mapToEncodingConfig(
            videoInfo = videoInfo(),
            settings = CompressionSettings.default()
        )
        val h264 = CompressionPresetMapper.mapToEncodingConfig(
            videoInfo = videoInfo(),
            settings = CompressionSettings.default().copy(codec = OutputCodec.H264_AVC)
        )

        assertEquals(MimeTypes.VIDEO_H264, h264.videoMimeType)
        assertTrue((h264.videoBitrate ?: 0) > (h265.videoBitrate ?: 0))
    }

    @Test
    fun targetSizeComputesApproximateVideoBitrate() {
        val config = CompressionPresetMapper.mapToEncodingConfig(
            videoInfo = videoInfo(durationMs = 10_000),
            settings = CompressionSettings.default().copy(targetSizeBytes = 5L * 1024L * 1024L)
        )

        assertNotNull(config.videoBitrate)
        assertTrue((config.videoBitrate ?: 0) > 0)
    }

    @Test
    fun estimateComputesApproximateOutputSizeAndSavings() {
        val estimate = CompressionPresetMapper.estimate(
            videoInfo = videoInfo(durationMs = 10_000),
            settings = CompressionSettings.default().copy(
                customVideoBitrate = 1_000_000,
                customAudioBitrate = 128_000
            )
        )

        assertEquals(1_410_000L, estimate.estimatedOutputSizeBytes)
        assertNotNull(estimate.estimatedSavingsPercent)
        assertTrue((estimate.estimatedSavingsPercent ?: 0f) > 0f)
    }

    @Test
    fun originalResolutionDoesNotUpscaleSmallVideo() {
        val config = CompressionPresetMapper.mapToEncodingConfig(
            videoInfo = videoInfo(width = 1280, height = 720, fps = 24f),
            settings = CompressionSettings.default()
        )

        assertNull(config.outputWidth)
        assertNull(config.outputHeight)
        assertNull(config.fps)
    }

    @Test
    fun rotatedPortraitVideoKeepsPortraitOutputDimensions() {
        val config = CompressionPresetMapper.mapToEncodingConfig(
            videoInfo = videoInfo(width = 3840, height = 2160, rotationDegrees = 90),
            settings = CompressionSettings.default()
        )

        assertEquals(1080, config.outputWidth)
        assertEquals(1920, config.outputHeight)
    }

    private fun videoInfo(
        width: Int = 1920,
        height: Int = 1080,
        fps: Float? = 30f,
        durationMs: Long? = 60_000,
        rotationDegrees: Int? = 0
    ) = VideoInfo(
        uri = mock(Uri::class.java),
        displayName = "sample.mp4",
        sizeBytes = 100L * 1024L * 1024L,
        durationMs = durationMs,
        width = width,
        height = height,
        fps = fps,
        videoCodec = "video/hevc",
        audioCodec = "audio/mp4a-latm",
        hasAudio = true,
        isHdr = false,
        rotationDegrees = rotationDegrees
    )
}
