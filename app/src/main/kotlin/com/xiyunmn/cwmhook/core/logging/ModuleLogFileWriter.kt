package com.xiyunmn.cwmhook.core.logging

import java.io.File
import java.io.FileWriter

internal class ModuleLogFileWriter(
    private val dir: File,
    private val logFileName: String,
    private val maxLogBytes: Long,
    private val maxBackupCount: Int,
) {
    @Synchronized
    fun write(lines: List<String>) {
        if (!dir.exists() && !dir.mkdirs()) {
            return
        }
        val file = File(dir, logFileName)
        pruneBackups()
        rotateIfNeeded(file, incomingBytes = estimateBytes(lines))
        FileWriter(file, true).use { writer ->
            lines.forEach { line ->
                writer.append(line).append('\n')
            }
        }
    }

    @Synchronized
    fun clear(): Boolean {
        if (!dir.exists()) {
            return true
        }
        val files = dir.listFiles() ?: return true
        var success = true
        files.forEach { file ->
            if (file.isFile && !file.delete() && file.exists()) {
                success = false
            }
        }
        return success
    }

    private fun estimateBytes(lines: List<String>): Long {
        return lines.sumOf { it.toByteArray(Charsets.UTF_8).size.toLong() + 1L }
    }

    private fun pruneBackups() {
        val files = dir.listFiles() ?: return
        files.forEach { file ->
            val suffix = file.name.removePrefix("$logFileName.")
            val index = suffix.toIntOrNull()
            if (index != null && index > maxBackupCount) {
                file.delete()
            }
        }
    }

    private fun rotateIfNeeded(file: File, incomingBytes: Long) {
        if (!file.exists() || file.length() + incomingBytes <= maxLogBytes) {
            return
        }
        File(dir, "$logFileName.$maxBackupCount").delete()
        for (index in maxBackupCount - 1 downTo 1) {
            val source = File(dir, "$logFileName.$index")
            if (source.exists()) {
                source.renameTo(File(dir, "$logFileName.${index + 1}"))
            }
        }
        file.renameTo(File(dir, "$logFileName.1"))
    }
}
