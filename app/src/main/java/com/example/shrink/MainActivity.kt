package com.example.shrink

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.shrink.settings.AppPreferences
import com.example.shrink.settings.AppearancePreferences
import com.example.shrink.settings.GeneralPreferences
import com.example.shrink.share.ShareIntentHandler
import com.example.shrink.ui.AppAccent
import com.example.shrink.ui.AppPage
import com.example.shrink.ui.CompressorScreen
import com.example.shrink.viewmodel.CompressorViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: CompressorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        setContent {
            val state by viewModel.uiState.collectAsState()
            val appPreferences = remember { AppPreferences(applicationContext) }
            val appearance by appPreferences.appearance.collectAsState(AppearancePreferences())
            val general by appPreferences.general.collectAsState(GeneralPreferences())
            val scope = rememberCoroutineScope()
            var page by rememberSaveable { mutableStateOf(AppPage.Compressor) }
            val accent = runCatching { enumValueOf<AppAccent>(appearance.accentName) }.getOrDefault(AppAccent.Purple)
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri != null) viewModel.selectVideo(uri)
            }
            val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    viewModel.selectVideo(uri)
                }
            }
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                viewModel.setNotificationsEnabled(granted)
            }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setNotificationsEnabled(true)
                }
            }
            val output = state.output
            LaunchedEffect(state.shouldAutoShare, state.output) {
                if (state.shouldAutoShare && output != null) {
                    shareVideo(output.contentUri)
                    viewModel.markAutoShareHandled()
                }
            }
            CompressorScreen(
                state = state,
                page = page,
                onPageChange = { page = it },
                darkMode = appearance.darkMode,
                onDarkModeChange = { darkMode ->
                    scope.launch { appPreferences.saveAppearance(appearance.copy(darkMode = darkMode)) }
                },
                accent = accent,
                onAccentChange = { selectedAccent ->
                    scope.launch { appPreferences.saveAppearance(appearance.copy(accentName = selectedAccent.name)) }
                },
                keepSourceDate = general.keepSourceDate,
                onKeepSourceDateChange = viewModel::setKeepSourceDate,
                onPickVideo = {
                    if (ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this)) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    } else {
                        documentPicker.launch(arrayOf("video/*"))
                    }
                },
                onSettingsChange = viewModel::updateSettings,
                onAdjustmentsChange = viewModel::updateAdjustments,
                onCompress = viewModel::compress,
                onCancel = viewModel::cancel,
                onClear = viewModel::clear,
                onShare = { state.output?.contentUri?.let(::shareVideo) },
                onOpen = { state.output?.contentUri?.let(::openVideo) },
                onSave = viewModel::saveToMovies,
                onRetryH264 = viewModel::retryWithH264
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri = ShareIntentHandler(this).incomingVideoUri(intent)
        if (uri == null) {
            viewModel.reportInvalidSharedInput()
        } else {
            viewModel.selectVideo(uri, launchedFromShareSheet = true)
        }
    }

    private fun shareVideo(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, "Share compressed video")) }
            .onFailure { viewModel.reportShareFailed() }
    }

    private fun openVideo(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.reportOpenFailed()
        }
    }
}
