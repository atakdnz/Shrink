package com.example.shrink.service

import com.example.shrink.compression.CompressionFailureReason
import com.example.shrink.compression.CompressionProgress
import java.io.File
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CompressionEventBus {
    private val _events = MutableSharedFlow<CompressionEvent>(replay = 1, extraBufferCapacity = 16)
    val events: SharedFlow<CompressionEvent> = _events.asSharedFlow()

    fun emit(event: CompressionEvent) {
        _events.tryEmit(event)
    }
}

sealed interface CompressionEvent {
    data object Preparing : CompressionEvent
    data class Progress(val progress: CompressionProgress) : CompressionEvent
    data class Success(val file: File, val outputSizeBytes: Long, val originalSizeBytes: Long?) : CompressionEvent
    data class Failure(val reason: CompressionFailureReason, val message: String) : CompressionEvent
    data object Cancelled : CompressionEvent
}
