package com.example.shrink.util

import java.util.Locale
import kotlin.math.abs

fun formatBytes(bytes: Long?): String {
    if (bytes == null) return "Size unknown"
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[index])
}

fun formatDuration(ms: Long?): String {
    if (ms == null) return "Duration unknown"
    val total = ms / 1000
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

fun formatResolution(width: Int?, height: Int?): String =
    if (width == null || height == null) "Resolution unknown" else "$width x $height"

fun formatPercent(value: Float?): String =
    if (value == null || value.isNaN()) "Unknown" else String.format(Locale.US, "%.1f%%", abs(value))
