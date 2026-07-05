package com.xiyunmn.cwmhook.core.logging

import java.io.File
import java.io.FileWriter

internal class ModuleLogFileWriter(
    private val dir: File,
    private val logFileName: String,
    private val maxLogBytes: Long,
    private val maxBackupCount: Int,
) {
    fun write(lines: List<String>) {
        if (!dir.exists() && !dir.mkdirs()) {
            return
        }
        val file = File(dir, logFileName)
        rotateIfNeeded(file)
        FileWriter(file, true).use { writer ->
            lines.forEach { line ->
                writer.append(line).append('\n')
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists() || file.length() < maxLogBytes) {
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
