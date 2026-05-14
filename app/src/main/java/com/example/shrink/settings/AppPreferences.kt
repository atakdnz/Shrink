package com.example.shrink.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.shrink.compression.AudioMode
import com.example.shrink.compression.CompressionPreset
import com.example.shrink.compression.CompressionSettings
import com.example.shrink.compression.FpsMode
import com.example.shrink.compression.OutputCodec
import com.example.shrink.compression.OutputResolution
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class AppearancePreferences(
    val darkMode: Boolean = false,
    val accentName: String = "Purple"
)

data class GeneralPreferences(
    val keepSourceDate: Boolean = true
)

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    val appearance: Flow<AppearancePreferences> = preferenceFlow { readAppearance() }
    val compressionSettings: Flow<CompressionSettings> = preferenceFlow { readCompressionSettings() }
    val general: Flow<GeneralPreferences> = preferenceFlow { readGeneral() }

    suspend fun saveAppearance(preferences: AppearancePreferences) {
        this.preferences.edit()
            .putBoolean(DARK_MODE, preferences.darkMode)
            .putString(ACCENT_NAME, preferences.accentName)
            .apply()
    }

    suspend fun saveCompressionSettings(settings: CompressionSettings) {
        this.preferences.edit()
            .putString(PRESET, settings.preset.name)
            .putString(RESOLUTION, settings.resolution.name)
            .putString(CODEC, settings.codec.name)
            .putString(FPS_MODE, settings.fpsMode.name)
            .putString(AUDIO_MODE, settings.audioMode.name)
            .putOptionalLong(TARGET_SIZE, settings.targetSizeBytes)
            .putOptionalInt(VIDEO_BITRATE, settings.customVideoBitrate)
            .putOptionalInt(AUDIO_BITRATE, settings.customAudioBitrate)
            .apply()
    }

    suspend fun saveGeneral(preferences: GeneralPreferences) {
        this.preferences.edit()
            .putBoolean(KEEP_SOURCE_DATE, preferences.keepSourceDate)
            .apply()
    }

    private fun readAppearance() = AppearancePreferences(
        darkMode = preferences.getBoolean(DARK_MODE, false),
        accentName = preferences.getString(ACCENT_NAME, "Purple") ?: "Purple"
    )

    private fun readCompressionSettings() = CompressionSettings(
        preset = enumPreference(preferences.getString(PRESET, null), CompressionPreset.BALANCED),
        resolution = enumPreference(preferences.getString(RESOLUTION, null), OutputResolution.ORIGINAL),
        codec = enumPreference(preferences.getString(CODEC, null), OutputCodec.H265_HEVC),
        fpsMode = enumPreference(preferences.getString(FPS_MODE, null), FpsMode.ORIGINAL),
        audioMode = enumPreference(preferences.getString(AUDIO_MODE, null), AudioMode.KEEP),
        targetSizeBytes = preferences.getLongOrNull(TARGET_SIZE),
        customVideoBitrate = preferences.getIntOrNull(VIDEO_BITRATE),
        customAudioBitrate = preferences.getIntOrNull(AUDIO_BITRATE)
    )

    private fun readGeneral() = GeneralPreferences(
        keepSourceDate = preferences.getBoolean(KEEP_SOURCE_DATE, true)
    )

    private fun <T> preferenceFlow(read: () -> T): Flow<T> = callbackFlow {
        trySend(read())
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    private inline fun <reified T : Enum<T>> enumPreference(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private fun SharedPreferences.getLongOrNull(key: String): Long? =
        if (contains(key)) getLong(key, 0L).takeIf { it > 0L } else null

    private fun SharedPreferences.getIntOrNull(key: String): Int? =
        if (contains(key)) getInt(key, 0).takeIf { it > 0 } else null

    private fun SharedPreferences.Editor.putOptionalLong(key: String, value: Long?): SharedPreferences.Editor =
        if (value == null) remove(key) else putLong(key, value)

    private fun SharedPreferences.Editor.putOptionalInt(key: String, value: Int?): SharedPreferences.Editor =
        if (value == null) remove(key) else putInt(key, value)

    private companion object {
        const val PREFERENCES_NAME = "app_preferences"
        const val DARK_MODE = "dark_mode"
        const val ACCENT_NAME = "accent_name"
        const val KEEP_SOURCE_DATE = "keep_source_date"
        const val PRESET = "preset"
        const val RESOLUTION = "resolution"
        const val CODEC = "codec"
        const val FPS_MODE = "fps_mode"
        const val AUDIO_MODE = "audio_mode"
        const val TARGET_SIZE = "target_size"
        const val VIDEO_BITRATE = "video_bitrate"
        const val AUDIO_BITRATE = "audio_bitrate"
    }
}
