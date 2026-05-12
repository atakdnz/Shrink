package com.example.shrink.compression

interface VideoCompressionEngine {
    suspend fun compress(
        input: CompressionInput,
        settings: CompressionSettings,
        output: CompressionOutput,
        progress: suspend (CompressionProgress) -> Unit
    ): CompressionResult

    fun cancel()
}
