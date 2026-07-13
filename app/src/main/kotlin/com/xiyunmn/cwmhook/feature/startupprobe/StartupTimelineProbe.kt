package com.xiyunmn.cwmhook.feature.startupprobe

import android.content.Context
import android.os.SystemClock
import com.xiyunmn.cwmhook.config.debug.DebugConfigStore
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

object StartupTimelineProbe {
    private const val TAG = "CWMHook.StartupProbe"
    private const val MAX_EVENTS = 96
    private const val MAX_TIMELINE_CHARS = 3600

    private val lock = Any()
    private val events = ArrayList<Event>(MAX_EVENTS)
    private val dumpedReasons = HashSet<String>()

    @Volatile
    private var enabled = false
    private var baseElapsedMs = 0L

    fun mark(name: String, detail: String? = null) {
        val event = Event(nowMs(), name, detail)
        synchronized(lock) {
            if (baseElapsedMs == 0L) {
                baseElapsedMs = event.elapsedMs
            }
            if (events.size < MAX_EVENTS) {
                events += event
            }
        }
    }

    fun configure(context: Context, reason: String) {
        val nextEnabled = runCatching {
            DebugConfigStore.readLocal(context).detailedFileLogEnabled
        }.getOrDefault(false)
        val changed = enabled != nextEnabled
        enabled = nextEnabled
        if (nextEnabled && changed) {
            dumpOnce("probeEnabled:$reason")
        }
    }

    fun dumpOnce(reason: String) {
        if (!enabled) {
            return
        }
        val line = synchronized(lock) {
            if (!dumpedReasons.add(reason)) {
                return
            }
            buildTimelineLocked(reason)
        }
        ModuleFileLogger.i(TAG, line)
    }

    private fun buildTimelineLocked(reason: String): String {
        val base = baseElapsedMs.takeIf { it > 0L } ?: nowMs()
        val body = events.joinToString(separator = " | ") { event ->
            buildString {
                append('+')
                append(event.elapsedMs - base)
                append("ms:")
                append(event.name)
                event.detail?.takeIf { it.isNotBlank() }?.let {
                    append('(')
                    append(it)
                    append(')')
                }
            }
        }
        val line = "timeline reason=$reason events=$body"
        return if (line.length <= MAX_TIMELINE_CHARS) {
            line
        } else {
            line.take(MAX_TIMELINE_CHARS) + "...truncated"
        }
    }

    private fun nowMs(): Long = SystemClock.elapsedRealtime()

    private data class Event(
        val elapsedMs: Long,
        val name: String,
        val detail: String?,
    )
}
