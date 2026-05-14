package com.example.shrink.compression

interface VideoCompressionEngine {
    suspend fun compress(
        input: CompressionInput,
        settings: CompressionSettings,
        adjustments: VideoAdjustments = VideoAdjustments(),
        output: CompressionOutput,
        keepSourceDate: Boolean = true,
        progress: suspend (CompressionProgress) -> Unit
    ): CompressionResult

    fun cancel()
}
