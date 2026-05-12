package com.example.shrink.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.shrink.compression.AudioMode
import com.example.shrink.compression.CompressedVideo
import com.example.shrink.compression.CompressionFailureReason
import com.example.shrink.compression.CompressionJobState
import com.example.shrink.compression.CompressionPresetMapper
import com.example.shrink.compression.CompressionResult
import com.example.shrink.compression.CompressionSettings
import com.example.shrink.compression.CompressorUiState
import com.example.shrink.compression.OutputCodec
import com.example.shrink.compression.VideoInfo
import com.example.shrink.metadata.VideoMetadataReader
import com.example.shrink.service.CompressionEvent
import com.example.shrink.service.CompressionEventBus
import com.example.shrink.service.CompressionForegroundService
import com.example.shrink.service.ServiceCompressionRequest
import com.example.shrink.storage.MediaStoreSaver
import com.example.shrink.storage.OutputFileManager
import com.example.shrink.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CompressorViewModel(application: Application) : AndroidViewModel(application) {
    private val metadataReader = VideoMetadataReader(application)
    private val outputFileManager = OutputFileManager(application)
    private val mediaStoreSaver = MediaStoreSaver(application)
    private val _uiState = MutableStateFlow(CompressorUiState())
    val uiState: StateFlow<CompressorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            CompressionEventBus.events.collect { event -> handleEvent(event) }
        }
    }

    fun selectVideo(uri: Uri, launchedFromShareSheet: Boolean = false) {
        _uiState.update {
            it.copy(
                jobState = CompressionJobState.LoadingMetadata,
                errorMessage = null,
                warningMessage = null,
                output = null,
                launchedFromShareSheet = launchedFromShareSheet
            )
        }
        viewModelScope.launch {
            val videoInfo = withContext(Dispatchers.IO) {
                if (!metadataReader.canOpenVideo(uri)) null else runCatching { metadataReader.read(uri) }.getOrNull()
            }
            if (videoInfo == null) {
                _uiState.update {
                    it.copy(
                        jobState = CompressionJobState.Failed(CompressionFailureReason.INPUT_URI_INVALID, "This video could not be opened. Try selecting it from Files instead."),
                        errorMessage = "This video could not be opened. Try selecting it from Files instead."
                    )
                }
            } else {
                val settings = defaultSettingsFor(videoInfo)
                val warning = if (videoInfo.isHdr == true) "HDR video detected. Compressed output may lose HDR/color accuracy." else null
                _uiState.update {
                    it.copy(
                        selectedVideo = videoInfo,
                        settings = settings,
                        jobState = CompressionJobState.Idle,
                        warningMessage = warning,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun updateSettings(settings: CompressionSettings) {
        val warning = _uiState.value.selectedVideo?.let { CompressionPresetMapper.mapToEncodingConfig(it, settings).warning }
        _uiState.update { it.copy(settings = settings, warningMessage = warning ?: it.warningMessage) }
    }

    fun compress() {
        val video = _uiState.value.selectedVideo ?: return
        if (!outputFileManager.hasWorkingSpaceFor(video.sizeBytes)) {
            _uiState.update {
                it.copy(
                    jobState = CompressionJobState.Failed(CompressionFailureReason.STORAGE_INSUFFICIENT, "Not enough storage space to compress this video."),
                    errorMessage = "Not enough storage space to compress this video."
                )
            }
            return
        }
        val settings = _uiState.value.settings
        val managed = outputFileManager.createOutput(video.displayName)
        val request = ServiceCompressionRequest(video, settings, managed.tempFile, managed.finalFile)
        _uiState.update { it.copy(jobState = CompressionJobState.Preparing, errorMessage = null, output = null) }
        runCatching { CompressionForegroundService.start(getApplication(), request) }
            .onFailure {
                _uiState.update { state ->
                    state.copy(
                        jobState = CompressionJobState.Failed(CompressionFailureReason.UNKNOWN, "Could not start foreground compression service."),
                        errorMessage = "Could not start foreground compression service."
                    )
                }
            }
    }

    fun cancel() {
        _uiState.update { it.copy(jobState = CompressionJobState.Cancelling) }
        CompressionForegroundService.cancel(getApplication())
    }

    fun clear() {
        _uiState.value.output?.file?.let { }
        _uiState.value = CompressorUiState()
    }

    fun retryWithH264() {
        updateSettings(_uiState.value.settings.copy(codec = OutputCodec.H264_AVC))
        compress()
    }

    fun markAutoShareHandled() {
        _uiState.update { it.copy(shouldAutoShare = false) }
    }

    fun saveToMovies() {
        val file = _uiState.value.output?.file ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { mediaStoreSaver.save(file) }
            _uiState.update {
                it.copy(
                    savedMessage = if (result.isSuccess) "Saved to Movies/VideoCompressor." else "Save failed: ${result.exceptionOrNull()?.message ?: "Unknown error"}"
                )
            }
        }
    }

    fun reportShareFailed() {
        _uiState.update { it.copy(errorMessage = "Share failed. No compatible app could receive this video.") }
    }

    fun reportOpenFailed() {
        _uiState.update { it.copy(errorMessage = "No app is available to open this video.") }
    }

    fun reportInvalidSharedInput() {
        _uiState.update {
            it.copy(
                jobState = CompressionJobState.Failed(CompressionFailureReason.INPUT_URI_INVALID, "Shared content is not a supported video."),
                errorMessage = "Shared content is not a supported video."
            )
        }
    }

    private fun handleEvent(event: CompressionEvent) {
        when (event) {
            CompressionEvent.Preparing -> _uiState.update { it.copy(jobState = CompressionJobState.Preparing) }
            is CompressionEvent.Progress -> _uiState.update { it.copy(jobState = CompressionJobState.Running(event.progress.percent)) }
            is CompressionEvent.Success -> {
                val uri = outputFileManager.contentUri(event.file)
                val ratio = event.originalSizeBytes?.takeIf { it > 0 }?.let { (1f - event.outputSizeBytes.toFloat() / it) * 100f }
                val warning = if (ratio != null && ratio < 0f) {
                    "Compressed output is larger than the original (${formatBytes(event.outputSizeBytes)} vs ${formatBytes(event.originalSizeBytes)})."
                } else null
                val success = CompressionResult.Success(event.file, event.outputSizeBytes, event.originalSizeBytes, uri)
                _uiState.update {
                    it.copy(
                        jobState = CompressionJobState.Success(success),
                        output = CompressedVideo(event.file, uri, event.originalSizeBytes, event.outputSizeBytes, ratio),
                        warningMessage = warning,
                        shouldAutoShare = it.launchedFromShareSheet
                    )
                }
            }
            is CompressionEvent.Failure -> _uiState.update {
                it.copy(
                    jobState = CompressionJobState.Failed(event.reason, event.message),
                    errorMessage = if (event.reason == CompressionFailureReason.H265_UNAVAILABLE) {
                        "H.265 encoding is not available on this device. Retry with H.264?"
                    } else event.message
                )
            }
            CompressionEvent.Cancelled -> _uiState.update { it.copy(jobState = CompressionJobState.Cancelled, errorMessage = "Compression cancelled.") }
        }
    }

    private fun defaultSettingsFor(videoInfo: VideoInfo): CompressionSettings {
        val needsFpsCap = (videoInfo.fps ?: 0f) > 30f
        return CompressionSettings.default().copy(
            resolution = if (maxOf(videoInfo.width ?: 0, videoInfo.height ?: 0) > 1080) com.example.shrink.compression.OutputResolution.P1080 else com.example.shrink.compression.OutputResolution.ORIGINAL,
            fpsMode = if (needsFpsCap) com.example.shrink.compression.FpsMode.FPS_30 else com.example.shrink.compression.FpsMode.ORIGINAL,
            audioMode = AudioMode.KEEP
        )
    }
}
