package com.example.shrink.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.shrink.compression.AudioMode
import com.example.shrink.compression.CompressionPreset
import com.example.shrink.compression.CompressionSettings
import com.example.shrink.compression.FpsMode
import com.example.shrink.compression.OutputCodec
import com.example.shrink.compression.OutputResolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.appDataStore by preferencesDataStore(name = "app_preferences")

data class AppearancePreferences(
    val darkMode: Boolean = false,
    val accentName: String = "Purple"
)

data class GeneralPreferences(
    val keepSourceDate: Boolean = true
)

class AppPreferences(context: Context) {
    private val dataStore = context.applicationContext.appDataStore

    val appearance: Flow<AppearancePreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw exception
        }
        .map { preferences ->
            AppearancePreferences(
                darkMode = preferences[DARK_MODE] ?: false,
                accentName = preferences[ACCENT_NAME] ?: "Purple"
            )
        }

    val compressionSettings: Flow<CompressionSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw exception
        }
        .map { preferences ->
            CompressionSettings(
                preset = enumPreference(preferences[PRESET], CompressionPreset.BALANCED),
                resolution = enumPreference(preferences[RESOLUTION], OutputResolution.ORIGINAL),
                codec = enumPreference(preferences[CODEC], OutputCodec.H265_HEVC),
                fpsMode = enumPreference(preferences[FPS_MODE], FpsMode.ORIGINAL),
                audioMode = enumPreference(preferences[AUDIO_MODE], AudioMode.KEEP),
                targetSizeBytes = preferences[TARGET_SIZE].takeIf { it != null && it > 0L },
                customVideoBitrate = preferences[VIDEO_BITRATE].takeIf { it != null && it > 0 },
                customAudioBitrate = preferences[AUDIO_BITRATE].takeIf { it != null && it > 0 }
            )
        }

    val general: Flow<GeneralPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw exception
        }
        .map { preferences ->
            GeneralPreferences(
                keepSourceDate = preferences[KEEP_SOURCE_DATE] ?: true
            )
        }

    suspend fun saveAppearance(preferences: AppearancePreferences) {
        dataStore.edit {
            it[DARK_MODE] = preferences.darkMode
            it[ACCENT_NAME] = preferences.accentName
        }
    }

    suspend fun saveCompressionSettings(settings: CompressionSettings) {
        dataStore.edit {
            it[PRESET] = settings.preset.name
            it[RESOLUTION] = settings.resolution.name
            it[CODEC] = settings.codec.name
            it[FPS_MODE] = settings.fpsMode.name
            it[AUDIO_MODE] = settings.audioMode.name
            setOptionalLong(it, TARGET_SIZE, settings.targetSizeBytes)
            setOptionalInt(it, VIDEO_BITRATE, settings.customVideoBitrate)
            setOptionalInt(it, AUDIO_BITRATE, settings.customAudioBitrate)
        }
    }

    suspend fun saveGeneral(preferences: GeneralPreferences) {
        dataStore.edit {
            it[KEEP_SOURCE_DATE] = preferences.keepSourceDate
        }
    }

    private inline fun <reified T : Enum<T>> enumPreference(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback

    private fun setOptionalLong(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<Long>,
        value: Long?
    ) {
        if (value == null) preferences.remove(key) else preferences[key] = value
    }

    private fun setOptionalInt(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: androidx.datastore.preferences.core.Preferences.Key<Int>,
        value: Int?
    ) {
        if (value == null) preferences.remove(key) else preferences[key] = value
    }

    private companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val ACCENT_NAME = stringPreferencesKey("accent_name")
        val KEEP_SOURCE_DATE = booleanPreferencesKey("keep_source_date")
        val PRESET = stringPreferencesKey("preset")
        val RESOLUTION = stringPreferencesKey("resolution")
        val CODEC = stringPreferencesKey("codec")
        val FPS_MODE = stringPreferencesKey("fps_mode")
        val AUDIO_MODE = stringPreferencesKey("audio_mode")
        val TARGET_SIZE = longPreferencesKey("target_size")
        val VIDEO_BITRATE = intPreferencesKey("video_bitrate")
        val AUDIO_BITRATE = intPreferencesKey("audio_bitrate")
    }
}
