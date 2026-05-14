package com.example.shrink.metadata

import java.io.File
import java.io.RandomAccessFile

object Mp4DateMetadataWriter {
    private const val QUICKTIME_EPOCH_OFFSET_SECONDS = 2_082_844_800L
    private val containerBoxes = setOf("moov", "trak", "mdia")
    private val dateBoxes = setOf("mvhd", "tkhd", "mdhd")

    fun writeCreationDate(file: File, capturedAtMillis: Long): Boolean {
        if (!file.exists() || file.length() < 16L) return false
        val mp4Seconds = capturedAtMillis / 1000L + QUICKTIME_EPOCH_OFFSET_SECONDS
        return runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                patchBoxes(raf, start = 0L, end = raf.length(), mp4Seconds = mp4Seconds) > 0
            }
        }.getOrDefault(false)
    }

    private fun patchBoxes(raf: RandomAccessFile, start: Long, end: Long, mp4Seconds: Long): Int {
        var offset = start
        var patched = 0
        while (offset + 8 <= end) {
            raf.seek(offset)
            val size32 = raf.readUnsignedInt()
            val type = raf.readAscii(4)
            val headerSize: Long
            val boxSize: Long
            when (size32) {
                0L -> {
                    headerSize = 8L
                    boxSize = end - offset
                }
                1L -> {
                    if (offset + 16 > end) return patched
                    headerSize = 16L
                    boxSize = raf.readLong()
                }
                else -> {
                    headerSize = 8L
                    boxSize = size32
                }
            }
            if (boxSize < headerSize || offset + boxSize > end) return patched
            val payloadStart = offset + headerSize
            val payloadEnd = offset + boxSize
            patched += when {
                type in dateBoxes -> patchDateBox(raf, payloadStart, payloadEnd, mp4Seconds)
                type in containerBoxes -> patchBoxes(raf, payloadStart, payloadEnd, mp4Seconds)
                else -> 0
            }
            offset += boxSize
        }
        return patched
    }

    private fun patchDateBox(raf: RandomAccessFile, payloadStart: Long, payloadEnd: Long, mp4Seconds: Long): Int {
        if (payloadStart + 12 > payloadEnd) return 0
        raf.seek(payloadStart)
        val version = raf.readUnsignedByte()
        val dateOffset = payloadStart + 4
        return when (version) {
            0 -> {
                if (dateOffset + 8 > payloadEnd || mp4Seconds > UInt.MAX_VALUE.toLong()) return 0
                raf.seek(dateOffset)
                raf.writeInt(mp4Seconds.toInt())
                raf.writeInt(mp4Seconds.toInt())
                1
            }
            1 -> {
                if (dateOffset + 16 > payloadEnd) return 0
                raf.seek(dateOffset)
                raf.writeLong(mp4Seconds)
                raf.writeLong(mp4Seconds)
                1
            }
            else -> 0
        }
    }

    private fun RandomAccessFile.readUnsignedInt(): Long = readInt().toLong() and 0xffffffffL

    private fun RandomAccessFile.readAscii(length: Int): String {
        val bytes = ByteArray(length)
        readFully(bytes)
        return bytes.decodeToString()
    }
}
