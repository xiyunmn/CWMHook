package com.xiyunmn.cwmhook.core.logging

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.os.SystemClock
import android.util.Log
import java.io.File

object ModuleFileLogger {
    private const val TAG = "CWMHook.FileLogger"
    private const val LOG_DIR = "cwmhook/logs"
    private const val LOG_FILE = "cwmhook.log"
    private const val MAX_LOG_BYTES = 768 * 1024L
    private const val MAX_BACKUP_COUNT = 4
    private const val MAX_EARLY_LINES = 240
    private const val MAX_THROTTLE_KEYS = 256

    private val lock = Any()
    private val earlyLines = ArrayDeque<String>()
    private val throttler = ModuleLogThrottler(MAX_THROTTLE_KEYS)

    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var writer: ModuleLogFileWriter? = null
    private var initialized = false
    private var droppedEarlyLines = 0

    fun init(context: Context, processName: String) {
        val appContext = context.applicationContext ?: context
        val dir = File(appContext.filesDir, LOG_DIR)
        val linesToFlush: List<String>
        synchronized(lock) {
            if (initialized) {
                return
            }
            initialized = true
            writer = ModuleLogFileWriter(
                dir = dir,
                logFileName = LOG_FILE,
                maxLogBytes = MAX_LOG_BYTES,
                maxBackupCount = MAX_BACKUP_COUNT,
            )
            val thread = HandlerThread("CWMHookFileLogger", Process.THREAD_PRIORITY_BACKGROUND)
            thread.start()
            handlerThread = thread
            handler = Handler(thread.looper)
            linesToFlush = buildList {
                add(ModuleLogLineFormatter.format(Log.INFO, TAG, "Logger initialized"))
                add(ModuleLogLineFormatter.format(Log.INFO, TAG, "process=$processName"))
                add(ModuleLogLineFormatter.format(Log.INFO, TAG, "path=${File(dir, LOG_FILE).absolutePath}"))
                if (droppedEarlyLines > 0) {
                    add(ModuleLogLineFormatter.format(Log.WARN, TAG, "Dropped $droppedEarlyLines early log lines before initialization"))
                }
                while (earlyLines.isNotEmpty()) {
                    add(earlyLines.removeFirst())
                }
            }
        }
        enqueue(linesToFlush)
    }

    fun i(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.INFO, tag, message, throwable)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.WARN, tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        log(Log.ERROR, tag, message, throwable)
    }

    fun throttled(
        key: String,
        intervalMs: Long,
        priority: Int,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val now = SystemClock.uptimeMillis()
        if (!throttler.shouldLog(key, intervalMs, now)) {
            return
        }
        log(priority, tag, message, throwable)
    }

    fun log(priority: Int, tag: String, message: String, throwable: Throwable? = null) {
        val line = ModuleLogLineFormatter.format(priority, tag, message, throwable)
        val currentHandler = synchronized(lock) {
            handler.also {
                if (it == null) {
                    if (earlyLines.size >= MAX_EARLY_LINES) {
                        earlyLines.removeFirst()
                        droppedEarlyLines += 1
                    }
                    earlyLines.addLast(line)
                }
            }
        }
        currentHandler?.post {
            writeLines(listOf(line))
        }
    }

    private fun enqueue(lines: List<String>) {
        val currentHandler = synchronized(lock) { handler }
        currentHandler?.post {
            writeLines(lines)
        }
    }

    private fun writeLines(lines: List<String>) {
        val currentWriter = synchronized(lock) { writer } ?: return
        try {
            currentWriter.write(lines)
        } catch (throwable: Throwable) {
            Log.e(TAG, "Failed to write module log", throwable)
        }
    }
}
