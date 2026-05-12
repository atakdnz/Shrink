package com.example.shrink.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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

private val ShrinkColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = Color(0xFF0F3F3A),
    secondary = Color(0xFF334155),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE2E8F0),
    error = Color(0xFFB42318),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A),
    tertiaryContainer = Color(0xFFFFF7D6),
    onTertiaryContainer = Color(0xFF5C4300)
)

private val ShrinkDarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF042F2E),
    primaryContainer = Color(0xFF115E59),
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = Color(0xFFCBD5E1),
    background = Color(0xFF0F172A),
    surface = Color(0xFF111827),
    surfaceVariant = Color(0xFF334155),
    error = Color(0xFFFCA5A5),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
    tertiaryContainer = Color(0xFF713F12),
    onTertiaryContainer = Color(0xFFFEF3C7)
)

private val QualityOptions = listOf(CompressionPreset.HIGH, CompressionPreset.BALANCED, CompressionPreset.SMALL, CompressionPreset.TINY)

@Composable
fun CompressorScreen(
    state: CompressorUiState,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
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
    MaterialTheme(colorScheme = if (darkMode) ShrinkDarkColors else ShrinkColors) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Header(darkMode, onDarkModeChange) }
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
                        item { ActionSection(state, onCompress, onCancel, onClear, onRetryH264) }
                        state.output?.let { item { ResultSection(it, onShare, onOpen, onSave, onClear) } }
                        state.savedMessage?.let { item { MessageCard(it, MessageTone.Success) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(darkMode: Boolean, onDarkModeChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Shrink", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Video compressor", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dark", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            Switch(checked = darkMode, onCheckedChange = onDarkModeChange)
        }
    }
}

@Composable
private fun EmptyState(onPickVideo: () -> Unit) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pick a video", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose a video here, or share one from Gallery or Files.",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Button(onClick = onPickVideo, modifier = Modifier.fillMaxWidth()) {
                Text("Pick Video")
            }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetadataCard(video: VideoInfo) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    video.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatBytes(video.sizeBytes), color = MaterialTheme.colorScheme.secondary)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatChip("Duration", formatDuration(video.durationMs))
                StatChip("Resolution", formatResolution(video.width, video.height))
                StatChip("FPS", video.fps?.let { "${it.toInt()}" } ?: "Unknown")
                StatChip("Video", video.videoCodec?.codecLabel() ?: "Unknown")
                StatChip("Audio", if (video.hasAudio) video.audioCodec?.codecLabel() ?: "Present" else "None")
                if (video.isHdr == true) StatChip("HDR", "Detected", emphasized = true)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, emphasized: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (emphasized) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, if (emphasized) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.width(132.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (emphasized) MaterialTheme.colorScheme.error else Color.Unspecified
            )
        }
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
        Text(
            message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = colors.second,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSection(settings: CompressionSettings, onChange: (CompressionSettings) -> Unit) {
    var advanced by remember { mutableStateOf(false) }
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Compression", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OptionGroup("Quality") {
                ChoiceGrid(
                    values = QualityOptions,
                    selected = settings.preset,
                    label = { it.name.lowercase().replaceFirstChar(Char::uppercase) },
                    detail = {
                        when (it) {
                            CompressionPreset.HIGH -> "Larger"
                            CompressionPreset.BALANCED -> "Default"
                            CompressionPreset.SMALL -> "Smaller"
                            CompressionPreset.TINY -> "Smallest"
                            CompressionPreset.CUSTOM -> "Custom"
                        }
                    },
                    onSelected = { onChange(settings.copy(preset = it)) }
                )
            }
            OptionGroup("Resolution") {
                ChoiceGrid(
                    values = OutputResolution.entries,
                    selected = settings.resolution,
                    label = { it.label() },
                    detail = { if (it == OutputResolution.ORIGINAL) "No resize" else "Max ${it.label()}" },
                    onSelected = { onChange(settings.copy(resolution = it)) }
                )
            }
            OptionGroup("Codec") {
                ChoiceGrid(
                    values = OutputCodec.entries,
                    selected = settings.codec,
                    label = { if (it == OutputCodec.H265_HEVC) "H.265" else "H.264" },
                    detail = { if (it == OutputCodec.H265_HEVC) "Smaller" else "Compatible" },
                    onSelected = { onChange(settings.copy(codec = it)) }
                )
            }
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "Hide Advanced" else "Advanced")
            }
            if (advanced) AdvancedSettings(settings, onChange)
        }
    }
}

@Composable
private fun OptionGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceGrid(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    detail: (T) -> String,
    onSelected: (T) -> Unit
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelected(value) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.width(116.dp).padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label(value), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun AdvancedSettings(settings: CompressionSettings, onChange: (CompressionSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = settings.targetSizeBytes?.let { (it / (1024 * 1024)).toString() } ?: "",
            onValueChange = {
                onChange(settings.copy(preset = CompressionPreset.CUSTOM, targetSizeBytes = it.toLongOrNull()?.times(1024 * 1024)))
            },
            label = { Text("Approx target size MB") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = settings.customVideoBitrate?.let { (it / 1000).toString() } ?: "",
                onValueChange = {
                    onChange(settings.copy(preset = CompressionPreset.CUSTOM, customVideoBitrate = it.toIntOrNull()?.times(1000)))
                },
                label = { Text("Video kbps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = settings.customAudioBitrate?.let { (it / 1000).toString() } ?: "",
                onValueChange = {
                    onChange(settings.copy(preset = CompressionPreset.CUSTOM, customAudioBitrate = it.toIntOrNull()?.times(1000)))
                },
                label = { Text("Audio kbps") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OptionGroup("FPS cap") {
            ChoiceGrid(
                values = FpsMode.entries,
                selected = settings.fpsMode,
                label = { it.label() },
                detail = { if (it == FpsMode.ORIGINAL) "Keep" else "Max" },
                onSelected = { onChange(settings.copy(preset = CompressionPreset.CUSTOM, fpsMode = it)) }
            )
        }
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.background) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Remove audio", fontWeight = FontWeight.SemiBold)
                    Text("Output video only", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                }
                Switch(
                    checked = settings.audioMode == AudioMode.REMOVE,
                    onCheckedChange = {
                        onChange(settings.copy(preset = CompressionPreset.CUSTOM, audioMode = if (it) AudioMode.REMOVE else AudioMode.KEEP))
                    }
                )
            }
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
private fun ResultSection(
    output: CompressedVideo,
    onShare: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onAnother: () -> Unit
) {
    Panel {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Compressed video ready", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ResultRow("Original", formatBytes(output.originalSizeBytes))
                    ResultRow("Output", formatBytes(output.outputSizeBytes))
                    output.compressionRatioPercent?.let {
                        ResultRow("Change", if (it >= 0f) "${formatPercent(it)} smaller" else "${formatPercent(it)} larger")
                    }
                }
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
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            content()
        }
    }
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
