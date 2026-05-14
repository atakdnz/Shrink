package com.example.shrink.compression

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.shrink.metadata.Mp4DateMetadataWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

@UnstableApi
class Media3CompressionEngine(private val context: Context) : VideoCompressionEngine {
    private var transformer: Transformer? = null

    override suspend fun compress(
        input: CompressionInput,
        settings: CompressionSettings,
        adjustments: VideoAdjustments,
        output: CompressionOutput,
        keepSourceDate: Boolean,
        progress: suspend (CompressionProgress) -> Unit
    ): CompressionResult = withContext(Dispatchers.Main) {
        val normalizedAdjustments = adjustments.normalized(input.videoInfo.durationMs)
        val config = CompressionPresetMapper.mapToEncodingConfig(input.videoInfo, settings, normalizedAdjustments)
        if (settings.codec == OutputCodec.H265_HEVC && !hasEncoder(config.videoMimeType)) {
            return@withContext CompressionResult.Failure(
                CompressionFailureReason.H265_UNAVAILABLE,
                "H.265 encoding is not available on this device."
            )
        }
        progress(CompressionProgress(0f, config.warning ?: "Preparing compression"))
        suspendCancellableCoroutine<CompressionResult> { continuation ->
            var progressJob: Job? = null
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                    progressJob?.cancel()
                    val final = runCatching {
                        output.finalFile.delete()
                        if (!output.tempFile.renameTo(output.finalFile)) {
                            output.tempFile.copyTo(output.finalFile, overwrite = true)
                            output.tempFile.delete()
                        }
                        output.finalFile
                    }.getOrElse {
                        continuation.resume(
                            CompressionResult.Failure(
                                CompressionFailureReason.OUTPUT_CREATION_FAILED,
                                "Compressed output could not be finalized.",
                                it
                            )
                        )
                        return
                    }
                    if (final.length() <= 0L) {
                        final.delete()
                        continuation.resume(
                            CompressionResult.Failure(
                                CompressionFailureReason.OUTPUT_CREATION_FAILED,
                                "Compressed output was empty."
                            )
                        )
                        return
                    }
                    input.videoInfo.capturedAtMillis?.takeIf { keepSourceDate }?.let {
                        Mp4DateMetadataWriter.writeCreationDate(final, it)
                        final.setLastModified(it)
                    }
                    val size = final.length()
                    val result = CompressionResult.Success(final, size, input.videoInfo.sizeBytes, null)
                    continuation.resume(result)
                }

                override fun onError(
                    composition: androidx.media3.transformer.Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    progressJob?.cancel()
                    output.tempFile.delete()
                    continuation.resume(
                        CompressionResult.Failure(
                            reasonFor(exportException, settings),
                            exportException.message ?: "Media3 export failed.",
                            exportException
                        )
                    )
                }
            }
            val encoderFactory = buildEncoderFactory(config)
            val editedItems = buildEditedItems(input, config, normalizedAdjustments)
            val activeTransformer = Transformer.Builder(context)
                .setVideoMimeType(config.videoMimeType)
                .setPortraitEncodingEnabled(true)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()
            transformer = activeTransformer
            continuation.invokeOnCancellation {
                progressJob?.cancel()
                activeTransformer.cancel()
                output.tempFile.delete()
            }
            if (editedItems.size == 1) {
                activeTransformer.start(editedItems.first(), output.tempFile.absolutePath)
            } else {
                val sequence = EditedMediaItemSequence.Builder(editedItems).build()
                activeTransformer.start(Composition.Builder(sequence).build(), output.tempFile.absolutePath)
            }
            progressJob = pollProgress(activeTransformer, progress)
        }
    }

    override fun cancel() {
        transformer?.cancel()
    }

    private fun pollProgress(transformer: Transformer, progress: suspend (CompressionProgress) -> Unit): Job =
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launchProgress(transformer, progress)

    private fun kotlinx.coroutines.CoroutineScope.launchProgress(
        transformer: Transformer,
        progress: suspend (CompressionProgress) -> Unit
    ): Job = launch {
        val holder = ProgressHolder()
        while (true) {
            val state = transformer.getProgress(holder)
            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                progress(CompressionProgress(holder.progress / 100f, null))
            }
            if (state == Transformer.PROGRESS_STATE_NOT_STARTED || state == Transformer.PROGRESS_STATE_UNAVAILABLE) {
                progress(CompressionProgress(0f, "Preparing compression"))
            }
            if (state == Transformer.PROGRESS_STATE_NO_TRANSFORMATION) return@launch
            delay(500)
        }
    }

    private fun buildEditedItems(
        input: CompressionInput,
        config: EncodingConfig,
        adjustments: VideoAdjustments
    ): List<EditedMediaItem> {
        val segments = adjustments.keptSegments.ifEmpty {
            input.videoInfo.durationMs?.takeIf { it > 0L }?.let { listOf(TimeRange(0L, it)) }.orEmpty()
        }
        val effects = buildEffects(config, adjustments)
        if (segments.isEmpty()) {
            return listOf(
                EditedMediaItem.Builder(MediaItem.fromUri(input.uri))
                    .setRemoveAudio(config.removeAudio)
                    .apply { if (effects != null) setEffects(effects) }
                    .build()
            )
        }
        return segments.map { segment ->
            val mediaItem = clippedMediaItem(input.uri, segment)
            EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(config.removeAudio)
                .apply { if (effects != null) setEffects(effects) }
                .build()
        }
    }

    private fun clippedMediaItem(uri: android.net.Uri, segment: TimeRange): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(segment.startMs)
                    .setEndPositionMs(segment.endMs)
                    .build()
            )
            .build()

    private fun buildEffects(config: EncodingConfig, adjustments: VideoAdjustments): Effects? {
        val videoEffects = mutableListOf<androidx.media3.common.Effect>()
        adjustments.crop?.let {
            videoEffects += Crop(
                -1f + (2f * it.leftFraction),
                1f - (2f * it.rightFraction),
                -1f + (2f * it.bottomFraction),
                1f - (2f * it.topFraction)
            )
        }
        if (adjustments.rotationDegrees != 0) {
            videoEffects += ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(adjustments.rotationDegrees.toFloat())
                .build()
        }
        config.fps?.let { videoEffects += FrameDropEffect.createDefaultFrameDropEffect(it.toFloat()) }
        if (config.outputWidth != null && config.outputHeight != null) {
            videoEffects += Presentation.createForWidthAndHeight(
                config.outputWidth,
                config.outputHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        }
        return if (videoEffects.isEmpty()) null else Effects(emptyList(), videoEffects)
    }

    private fun buildEncoderFactory(config: EncodingConfig): DefaultEncoderFactory {
        val builder = DefaultEncoderFactory.Builder(context).setEnableFallback(false)
        config.videoBitrate?.let {
            builder.setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(it).build())
        }
        config.audioBitrate?.let {
            builder.setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(it).build())
        }
        return builder.build()
    }

    private fun hasEncoder(mimeType: String): Boolean {
        val format = MediaFormat.createVideoFormat(mimeType, 1280, 720)
        val codecName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
        return codecName != null
    }

    private fun reasonFor(exception: ExportException, settings: CompressionSettings): CompressionFailureReason {
        val message = exception.message.orEmpty().lowercase()
        return when {
            settings.codec == OutputCodec.H265_HEVC && ("h265" in message || "hevc" in message) ->
                CompressionFailureReason.H265_UNAVAILABLE
            "decoder" in message -> CompressionFailureReason.DECODER_FAILED
            "encoder" in message -> CompressionFailureReason.ENCODER_FAILED
            else -> CompressionFailureReason.MEDIA3_EXPORT_FAILED
        }
    }
}
