package com.example.shrink.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import com.example.shrink.MainActivity
import com.example.shrink.R
import com.example.shrink.compression.AudioMode
import com.example.shrink.compression.CompressionInput
import com.example.shrink.compression.CompressionOutput
import com.example.shrink.compression.CompressionPreset
import com.example.shrink.compression.CompressionResult
import com.example.shrink.compression.CompressionSettings
import com.example.shrink.compression.FpsMode
import com.example.shrink.compression.Media3CompressionEngine
import com.example.shrink.compression.OutputCodec
import com.example.shrink.compression.OutputResolution
import com.example.shrink.compression.VideoInfo
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@UnstableApi
class CompressionForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var engine: Media3CompressionEngine? = null
    private var activeTempFile: File? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> cancelCompression()
            ACTION_START -> startCompression(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startCompression(intent: Intent) {
        val request = intent.toCompressionRequest() ?: run {
            CompressionEventBus.emit(CompressionEvent.Failure(com.example.shrink.compression.CompressionFailureReason.UNKNOWN, "Compression request was missing."))
            stopSelf()
            return
        }
        activeTempFile = request.tempFile
        startForeground(NOTIFICATION_ID, notification(0, true))
        CompressionEventBus.emit(CompressionEvent.Preparing)
        job = scope.launch {
            val activeEngine = Media3CompressionEngine(applicationContext)
            engine = activeEngine
            val result = activeEngine.compress(
                input = CompressionInput(request.videoInfo.uri, request.videoInfo),
                settings = request.settings,
                output = CompressionOutput(request.tempFile, request.finalFile),
                keepSourceDate = request.keepSourceDate
            ) { progress ->
                CompressionEventBus.emit(CompressionEvent.Progress(progress))
                updateNotification((progress.percent * 100).toInt())
            }
            when (result) {
                is CompressionResult.Success -> {
                    CompressionEventBus.emit(CompressionEvent.Success(result.outputFile, result.outputSizeBytes, result.originalSizeBytes))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                is CompressionResult.Failure -> {
                    request.tempFile.delete()
                    CompressionEventBus.emit(CompressionEvent.Failure(result.reason, result.message))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                CompressionResult.Cancelled -> cancelCompression()
            }
        }
    }

    private fun cancelCompression() {
        activeTempFile?.delete()
        engine?.cancel()
        job?.cancel()
        CompressionEventBus.emit(CompressionEvent.Cancelled)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(percent: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification(percent.coerceIn(0, 100), false))
    }

    private fun notification(percent: Int, indeterminate: Boolean) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Compressing video")
            .setContentText(if (indeterminate) "Preparing" else "$percent%")
            .setOngoing(true)
            .setProgress(100, percent, indeterminate)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), immutableFlag()))
            .addAction(0, "Cancel", PendingIntent.getService(this, 1, Intent(this, javaClass).setAction(ACTION_CANCEL), immutableFlag()))
            .build()

    private fun immutableFlag() = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Video compression", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "compression"
        private const val NOTIFICATION_ID = 17
        private const val ACTION_START = "com.example.shrink.START_COMPRESSION"
        private const val ACTION_CANCEL = "com.example.shrink.CANCEL_COMPRESSION"

        fun start(context: Context, request: ServiceCompressionRequest) {
            val intent = Intent(context, CompressionForegroundService::class.java)
                .setAction(ACTION_START)
                .putCompressionRequest(request)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, CompressionForegroundService::class.java).setAction(ACTION_CANCEL)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}

data class ServiceCompressionRequest(
    val videoInfo: VideoInfo,
    val settings: CompressionSettings,
    val tempFile: File,
    val finalFile: File,
    val keepSourceDate: Boolean
)

private fun Intent.putCompressionRequest(request: ServiceCompressionRequest): Intent = apply {
    putExtra(EXTRA_URI, request.videoInfo.uri)
    putExtra(EXTRA_DISPLAY_NAME, request.videoInfo.displayName)
    putExtra(EXTRA_SIZE_BYTES, request.videoInfo.sizeBytes ?: -1L)
    putExtra(EXTRA_DURATION_MS, request.videoInfo.durationMs ?: -1L)
    putExtra(EXTRA_WIDTH, request.videoInfo.width ?: -1)
    putExtra(EXTRA_HEIGHT, request.videoInfo.height ?: -1)
    putExtra(EXTRA_FPS, request.videoInfo.fps ?: -1f)
    putExtra(EXTRA_VIDEO_CODEC, request.videoInfo.videoCodec)
    putExtra(EXTRA_AUDIO_CODEC, request.videoInfo.audioCodec)
    putExtra(EXTRA_HAS_AUDIO, request.videoInfo.hasAudio)
    putExtra(EXTRA_IS_HDR, request.videoInfo.isHdr ?: false)
    putExtra(EXTRA_IS_HDR_KNOWN, request.videoInfo.isHdr != null)
    putExtra(EXTRA_ROTATION, request.videoInfo.rotationDegrees ?: -1)
    putExtra(EXTRA_CAPTURED_AT, request.videoInfo.capturedAtMillis ?: -1L)
    putExtra(EXTRA_PRESET, request.settings.preset.name)
    putExtra(EXTRA_RESOLUTION, request.settings.resolution.name)
    putExtra(EXTRA_CODEC, request.settings.codec.name)
    putExtra(EXTRA_FPS_MODE, request.settings.fpsMode.name)
    putExtra(EXTRA_AUDIO_MODE, request.settings.audioMode.name)
    putExtra(EXTRA_TARGET_SIZE, request.settings.targetSizeBytes ?: -1L)
    putExtra(EXTRA_VIDEO_BITRATE, request.settings.customVideoBitrate ?: -1)
    putExtra(EXTRA_AUDIO_BITRATE, request.settings.customAudioBitrate ?: -1)
    putExtra(EXTRA_KEEP_SOURCE_DATE, request.keepSourceDate)
    putExtra(EXTRA_TEMP_FILE, request.tempFile.absolutePath)
    putExtra(EXTRA_FINAL_FILE, request.finalFile.absolutePath)
}

private fun Intent.toCompressionRequest(): ServiceCompressionRequest? {
    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(EXTRA_URI, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(EXTRA_URI)
    } ?: return null
    val tempPath = getStringExtra(EXTRA_TEMP_FILE) ?: return null
    val finalPath = getStringExtra(EXTRA_FINAL_FILE) ?: return null
    val videoInfo = VideoInfo(
        uri = uri,
        displayName = getStringExtra(EXTRA_DISPLAY_NAME) ?: "video.mp4",
        sizeBytes = getLongExtra(EXTRA_SIZE_BYTES, -1L).takeIf { it >= 0L },
        durationMs = getLongExtra(EXTRA_DURATION_MS, -1L).takeIf { it >= 0L },
        width = getIntExtra(EXTRA_WIDTH, -1).takeIf { it >= 0 },
        height = getIntExtra(EXTRA_HEIGHT, -1).takeIf { it >= 0 },
        fps = getFloatExtra(EXTRA_FPS, -1f).takeIf { it >= 0f },
        videoCodec = getStringExtra(EXTRA_VIDEO_CODEC),
        audioCodec = getStringExtra(EXTRA_AUDIO_CODEC),
        hasAudio = getBooleanExtra(EXTRA_HAS_AUDIO, false),
        isHdr = if (getBooleanExtra(EXTRA_IS_HDR_KNOWN, false)) getBooleanExtra(EXTRA_IS_HDR, false) else null,
        rotationDegrees = getIntExtra(EXTRA_ROTATION, -1).takeIf { it >= 0 },
        capturedAtMillis = getLongExtra(EXTRA_CAPTURED_AT, -1L).takeIf { it > 0L }
    )
    val settings = CompressionSettings(
        preset = enumExtra(EXTRA_PRESET, CompressionPreset.BALANCED),
        resolution = enumExtra(EXTRA_RESOLUTION, OutputResolution.ORIGINAL),
        codec = enumExtra(EXTRA_CODEC, OutputCodec.H265_HEVC),
        fpsMode = enumExtra(EXTRA_FPS_MODE, FpsMode.ORIGINAL),
        audioMode = enumExtra(EXTRA_AUDIO_MODE, AudioMode.KEEP),
        targetSizeBytes = getLongExtra(EXTRA_TARGET_SIZE, -1L).takeIf { it > 0L },
        customVideoBitrate = getIntExtra(EXTRA_VIDEO_BITRATE, -1).takeIf { it > 0 },
        customAudioBitrate = getIntExtra(EXTRA_AUDIO_BITRATE, -1).takeIf { it > 0 }
    )
    return ServiceCompressionRequest(
        videoInfo = videoInfo,
        settings = settings,
        tempFile = File(tempPath),
        finalFile = File(finalPath),
        keepSourceDate = getBooleanExtra(EXTRA_KEEP_SOURCE_DATE, true)
    )
}

private inline fun <reified T : Enum<T>> Intent.enumExtra(key: String, fallback: T): T =
    getStringExtra(key)?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

private const val EXTRA_URI = "uri"
private const val EXTRA_DISPLAY_NAME = "display_name"
private const val EXTRA_SIZE_BYTES = "size_bytes"
private const val EXTRA_DURATION_MS = "duration_ms"
private const val EXTRA_WIDTH = "width"
private const val EXTRA_HEIGHT = "height"
private const val EXTRA_FPS = "fps"
private const val EXTRA_VIDEO_CODEC = "video_codec"
private const val EXTRA_AUDIO_CODEC = "audio_codec"
private const val EXTRA_HAS_AUDIO = "has_audio"
private const val EXTRA_IS_HDR = "is_hdr"
private const val EXTRA_IS_HDR_KNOWN = "is_hdr_known"
private const val EXTRA_ROTATION = "rotation"
private const val EXTRA_CAPTURED_AT = "captured_at"
private const val EXTRA_PRESET = "preset"
private const val EXTRA_RESOLUTION = "resolution"
private const val EXTRA_CODEC = "codec"
private const val EXTRA_FPS_MODE = "fps_mode"
private const val EXTRA_AUDIO_MODE = "audio_mode"
private const val EXTRA_TARGET_SIZE = "target_size"
private const val EXTRA_VIDEO_BITRATE = "video_bitrate"
private const val EXTRA_AUDIO_BITRATE = "audio_bitrate"
private const val EXTRA_KEEP_SOURCE_DATE = "keep_source_date"
private const val EXTRA_TEMP_FILE = "temp_file"
private const val EXTRA_FINAL_FILE = "final_file"
