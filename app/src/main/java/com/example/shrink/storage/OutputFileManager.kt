package com.example.shrink.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class OutputFileManager(private val context: Context) {
    private val root: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "VideoCompressor")
    private val tempDir: File get() = File(root, "temp")
    private val outputDir: File get() = File(root, "output")

    fun createOutput(originalName: String): ManagedOutput {
        tempDir.mkdirs()
        outputDir.mkdirs()
        val jobId = UUID.randomUUID().toString()
        val temp = File(tempDir, "$jobId.mp4")
        val safeBase = originalName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]"), "_")
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val final = File(outputDir, "${safeBase}_compressed_$stamp.mp4")
        return ManagedOutput(temp, final)
    }

    fun publishTemp(managedOutput: ManagedOutput): File {
        if (!managedOutput.tempFile.exists() || managedOutput.tempFile.length() <= 0L) {
            throw IllegalStateException("Output file missing or empty")
        }
        managedOutput.finalFile.delete()
        if (!managedOutput.tempFile.renameTo(managedOutput.finalFile)) {
            managedOutput.tempFile.copyTo(managedOutput.finalFile, overwrite = true)
            managedOutput.tempFile.delete()
        }
        return managedOutput.finalFile
    }

    fun deleteTemp(file: File?) {
        file?.takeIf { it.exists() }?.delete()
    }

    fun hasWorkingSpaceFor(inputSizeBytes: Long?): Boolean {
        val required = (inputSizeBytes ?: 250L * 1024L * 1024L).coerceAtLeast(250L * 1024L * 1024L)
        val stat = StatFs(root.apply { mkdirs() }.absolutePath)
        return stat.availableBytes > required
    }

    fun contentUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

data class ManagedOutput(val tempFile: File, val finalFile: File)
