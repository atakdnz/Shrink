package com.example.shrink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.shrink.compression.AudioMode
import com.example.shrink.compression.CompressionEstimate
import com.example.shrink.compression.CompressedVideo
import com.example.shrink.compression.CompressionFailureReason
import com.example.shrink.compression.CompressionJobState
import com.example.shrink.compression.CompressionPreset
import com.example.shrink.compression.CompressionSettings
import com.example.shrink.compression.CompressorUiState
import com.example.shrink.compression.FpsMode
import com.example.shrink.compression.OutputCodec
import com.example.shrink.compression.OutputResolution
import com.example.shrink.compression.VideoInfo
import com.example.shrink.util.formatBytes
import com.example.shrink.util.formatDuration
import com.example.shrink.util.formatPercent
import com.example.shrink.util.formatResolution

enum class AppPage { Compressor, Settings }

enum class AppAccent(val label: String, val light: Color, val lightContainer: Color, val dark: Color, val darkContainer: Color) {
    Purple("Purple", Color(0xFF6D28D9), Color(0xFFEDE9FE), Color(0xFFC4B5FD), Color(0xFF3B0764)),
    Blue("Blue", Color(0xFF2563EB), Color(0xFFDBEAFE), Color(0xFF93C5FD), Color(0xFF172554)),
    Green("Green", Color(0xFF15803D), Color(0xFFDCFCE7), Color(0xFF86EFAC), Color(0xFF052E16)),
    Red("Red", Color(0xFFB91C1C), Color(0xFFFEE2E2), Color(0xFFFCA5A5), Color(0xFF450A0A))
}

private fun appColors(darkMode: Boolean, accent: AppAccent) = if (darkMode) {
    darkColorScheme(
        primary = accent.dark,
        onPrimary = Color.Black,
        primaryContainer = accent.darkContainer,
        onPrimaryContainer = Color.White,
        secondary = Color(0xFFE5E7EB),
        background = Color.Black,
        surface = Color(0xFF09090B),
        surfaceVariant = Color(0xFF27272A),
        error = Color(0xFFFCA5A5),
        errorContainer = Color(0xFF450A0A),
        onErrorContainer = Color(0xFFFEE2E2),
        tertiaryContainer = Color(0xFF422006),
        onTertiaryContainer = Color(0xFFFEF3C7)
    )
} else {
    lightColorScheme(
        primary = accent.light,
        onPrimary = Color.White,
        primaryContainer = accent.lightContainer,
        onPrimaryContainer = Color(0xFF1F2937),
        secondary = Color(0xFF475569),
        background = Color(0xFFF8FAFC),
        surface = Color.White,
        surfaceVariant = Color(0xFFE2E8F0),
        error = Color(0xFFB42318),
        errorContainer = Color(0xFFFEE4E2),
        onErrorContainer = Color(0xFF7A271A),
        tertiaryContainer = Color(0xFFFFF7D6),
        onTertiaryContainer = Color(0xFF5C4300)
    )
}

@Composable
fun CompressorScreen(
    state: CompressorUiState,
    page: AppPage,
    onPageChange: (AppPage) -> Unit,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    accent: AppAccent,
    onAccentChange: (AppAccent) -> Unit,
    keepSourceDate: Boolean,
    onKeepSourceDateChange: (Boolean) -> Unit,
    onPickVideo: () -> Unit,
    onSettingsChange: (CompressionSettings) -> Unit,
    onCompress: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onRetryH264: () -> Unit
) {
    MaterialTheme(colorScheme = appColors(darkMode, accent)) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Header(page, onPageChange) }
                if (page == AppPage.Settings) {
                    item { AppSettings(darkMode, onDarkModeChange, accent, onAccentChange, keepSourceDate, onKeepSourceDateChange) }
                } else {
                    when {
                        state.selectedVideo == null && state.jobState !is CompressionJobState.LoadingMetadata -> {
                            item { EmptyState(onPickVideo) }
                            state.errorMessage?.let { item { MessageCard(it, MessageTone.Error) } }
                        }
                        state.jobState is CompressionJobState.LoadingMetadata -> item { LoadingCard("Reading video details") }
                        else -> {
                            state.selectedVideo?.let { item { MetadataCard(it) } }
                            state.warningMessage?.let { item { MessageCard(it, MessageTone.Warning) } }
                            state.errorMessage?.let { item { MessageCard(it, MessageTone.Error) } }
                            item { SettingsSection(state.settings, onSettingsChange) }
                            state.estimate?.let { item { EstimateSection(it) } }
                            item { ActionSection(state, onCompress, onCancel, onClear, onRetryH264) }
                            state.output?.let { item { ResultSection(it, onShare, onOpen, onSave, onClear) } }
                            state.savedMessage?.let { item { MessageCard(it, MessageTone.Success) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(page: AppPage, onPageChange: (AppPage) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Shrink", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Video compressor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        }
        TextButton(onClick = { onPageChange(if (page == AppPage.Settings) AppPage.Compressor else AppPage.Settings) }) {
            Text(if (page == AppPage.Settings) "Done" else "Settings")
        }
    }
}

@Composable
private fun AppearanceSettings(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    accent: AppAccent,
    onAccentChange: (AppAccent) -> Unit
) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            ToggleRow("Dark mode", "Use true black background", darkMode, onDarkModeChange)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Text("Primary color", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            AppAccent.entries.forEach {
                SelectRow(
                    title = it.label,
                    detail = if (it == AppAccent.Purple) "Default" else "Accent",
                    selected = it == accent,
                    onClick = { onAccentChange(it) }
                )
            }
        }
    }
}

@Composable
private fun AppSettings(
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    accent: AppAccent,
    onAccentChange: (AppAccent) -> Unit,
    keepSourceDate: Boolean,
    onKeepSourceDateChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppearanceSettings(darkMode, onDarkModeChange, accent, onAccentChange)
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Output", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                ToggleRow(
                    title = "Keep source date",
                    detail = "Save compressed videos with the original video date when available",
                    checked = keepSourceDate,
                    onCheckedChange = onKeepSourceDateChange
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onPickVideo: () -> Unit) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pick a video", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("Choose a video here, or share one from Gallery or Files.", color = MaterialTheme.colorScheme.secondary)
            }
            Button(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) { Text("Pick Video") }
        }
    }
}

@Composable
private fun LoadingCard(message: String) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MetadataCard(video: VideoInfo) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                video.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(formatBytes(video.sizeBytes), color = MaterialTheme.colorScheme.secondary)
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            MetadataRow("Duration", formatDuration(video.durationMs))
            MetadataRow("Resolution", formatResolution(video.width, video.height))
            MetadataRow("FPS", video.fps?.let { "${it.toInt()}" } ?: "Unknown")
            MetadataRow("Video", video.videoCodec?.codecLabel() ?: "Unknown")
            MetadataRow("Audio", if (video.hasAudio) video.audioCodec?.codecLabel() ?: "Present" else "None")
            if (video.isHdr == true) MetadataRow("HDR", "Detected")
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class MessageTone { Warning, Error, Success }

@Composable
private fun MessageCard(message: String, tone: MessageTone) {
    val colors = when (tone) {
        MessageTone.Warning -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        MessageTone.Error -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        MessageTone.Success -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(shape = RoundedCornerShape(8.dp), color = colors.first) {
        Text(message, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), color = colors.second)
    }
}

@Composable
private fun SettingsSection(settings: CompressionSettings, onChange: (CompressionSettings) -> Unit) {
    var advanced by remember { mutableStateOf(false) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Compression", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OptionGroup("Quality") {
                listOf(CompressionPreset.HIGH, CompressionPreset.BALANCED, CompressionPreset.SMALL, CompressionPreset.TINY).forEach {
                    SelectRow(it.label(), it.detail(), settings.preset == it) { onChange(settings.copy(preset = it)) }
                }
            }
            OptionGroup("Resolution") {
                OutputResolution.entries.forEach {
                    SelectRow(it.label(), if (it == OutputResolution.ORIGINAL) "No resize" else "Max ${it.label()}", settings.resolution == it) {
                        onChange(settings.copy(resolution = it))
                    }
                }
            }
            OptionGroup("Codec") {
                OutputCodec.entries.forEach {
                    SelectRow(if (it == OutputCodec.H265_HEVC) "H.265" else "H.264", if (it == OutputCodec.H265_HEVC) "Smaller output" else "Better compatibility", settings.codec == it) {
                        onChange(settings.copy(codec = it))
                    }
                }
            }
            TextButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
                Text(if (advanced) "Hide Advanced" else "Advanced")
            }
            if (advanced) AdvancedSettings(settings, onChange)
        }
    }
}

@Composable
private fun OptionGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SelectRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AdvancedSettings(settings: CompressionSettings, onChange: (CompressionSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = settings.targetSizeBytes?.let { (it / (1024 * 1024)).toString() } ?: "",
            onValueChange = { onChange(settings.copy(preset = CompressionPreset.CUSTOM, targetSizeBytes = it.toLongOrNull()?.times(1024 * 1024))) },
            label = { Text("Approx target size MB") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = settings.customVideoBitrate?.let { (it / 1000).toString() } ?: "",
                onValueChange = { onChange(settings.copy(preset = CompressionPreset.CUSTOM, customVideoBitrate = it.toIntOrNull()?.times(1000))) },
                label = { Text("Video kbps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = settings.customAudioBitrate?.let { (it / 1000).toString() } ?: "",
                onValueChange = { onChange(settings.copy(preset = CompressionPreset.CUSTOM, customAudioBitrate = it.toIntOrNull()?.times(1000))) },
                label = { Text("Audio kbps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OptionGroup("FPS cap") {
            FpsMode.entries.forEach {
                SelectRow(it.label(), if (it == FpsMode.ORIGINAL) "Keep source" else "Maximum FPS", settings.fpsMode == it) {
                    onChange(settings.copy(preset = CompressionPreset.CUSTOM, fpsMode = it))
                }
            }
        }
        ToggleRow(
            title = "Remove audio",
            detail = "Output video only",
            checked = settings.audioMode == AudioMode.REMOVE,
            onCheckedChange = { onChange(settings.copy(preset = CompressionPreset.CUSTOM, audioMode = if (it) AudioMode.REMOVE else AudioMode.KEEP)) }
        )
    }
}

@Composable
private fun EstimateSection(estimate: CompressionEstimate) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Estimate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            ResultRow("Output size", estimate.estimatedOutputSizeBytes?.let { "~${formatBytes(it)}" } ?: "Unknown")
            estimate.estimatedSavingsPercent?.let {
                ResultRow("Expected change", if (it >= 0f) "~${formatPercent(it)} smaller" else "~${formatPercent(it)} larger")
            }
            ResultRow("Resolution", formatResolution(estimate.outputWidth, estimate.outputHeight))
            ResultRow("Video bitrate", estimate.videoBitrate?.let { "${it / 1000} kbps" } ?: "Auto")
            ResultRow("Audio", if (estimate.removeAudio) "Removed" else estimate.audioBitrate?.let { "${it / 1000} kbps" } ?: "Keep")
            ResultRow("FPS cap", estimate.fps?.toString() ?: "Original")
            Text(
                "Approximate only. Actual size depends on the device encoder and source video.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun ActionSection(
    state: CompressorUiState,
    onCompress: () -> Unit,
    onCancel: () -> Unit,
    onClear: () -> Unit,
    onRetryH264: () -> Unit
) {
    when (val job = state.jobState) {
        CompressionJobState.Preparing -> ProgressPanel(null, "Preparing compression", onCancel)
        is CompressionJobState.Running -> ProgressPanel(job.progress, "Compressing ${(job.progress * 100).toInt()}%", onCancel)
        CompressionJobState.Cancelling -> LoadingCard("Cancelling compression")
        is CompressionJobState.Failed -> Panel {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (job.reason == CompressionFailureReason.H265_UNAVAILABLE) {
                    Button(onClick = onRetryH264, modifier = Modifier.fillMaxWidth()) { Text("Retry with H.264") }
                }
                PrimaryActions(onCompress, onClear)
            }
        }
        is CompressionJobState.Success -> Unit
        else -> Panel { PrimaryActions(onCompress, onClear) }
    }
}

@Composable
private fun PrimaryActions(onCompress: () -> Unit, onClear: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onCompress, modifier = Modifier.weight(1f)) { Text("Compress") }
        OutlinedButton(onClick = onClear) { Text("Clear") }
    }
}

@Composable
private fun ProgressPanel(progress: Float?, label: String, onCancel: () -> Unit) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            if (progress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
    }
}

@Composable
private fun ResultSection(output: CompressedVideo, onShare: () -> Unit, onOpen: () -> Unit, onSave: () -> Unit, onAnother: () -> Unit) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Compressed video ready", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            ResultRow("Original", formatBytes(output.originalSizeBytes))
            ResultRow("Output", formatBytes(output.outputSizeBytes))
            output.compressionRatioPercent?.let {
                ResultRow("Change", if (it >= 0f) "${formatPercent(it)} smaller" else "${formatPercent(it)} larger")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save") }
                TextButton(onClick = onAnother, modifier = Modifier.weight(1f)) { Text("Another") }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.secondary)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

private fun CompressionPreset.label() = name.lowercase().replaceFirstChar(Char::uppercase)

private fun CompressionPreset.detail() = when (this) {
    CompressionPreset.HIGH -> "Preserve quality"
    CompressionPreset.BALANCED -> "Default"
    CompressionPreset.SMALL -> "Smaller file"
    CompressionPreset.TINY -> "Smallest file"
    CompressionPreset.CUSTOM -> "Custom"
}

private fun OutputResolution.label() = when (this) {
    OutputResolution.ORIGINAL -> "Original"
    OutputResolution.P1080 -> "1080p"
    OutputResolution.P720 -> "720p"
    OutputResolution.P480 -> "480p"
    OutputResolution.P360 -> "360p"
}

private fun FpsMode.label() = when (this) {
    FpsMode.ORIGINAL -> "Original"
    FpsMode.FPS_60 -> "60"
    FpsMode.FPS_30 -> "30"
    FpsMode.FPS_24 -> "24"
}

private fun String.codecLabel() = substringAfter('/').uppercase()
