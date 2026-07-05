package com.xiyunmn.cwmhook.core.logging

internal class ModuleLogThrottler(
    private val maxKeys: Int,
) {
    private val times = LinkedHashMap<String, Long>()

    @Synchronized
    fun shouldLog(key: String, intervalMs: Long, nowMs: Long): Boolean {
        val last = times[key]
        if (last != null && nowMs - last < intervalMs) {
            return false
        }
        times[key] = nowMs
        if (times.size > maxKeys) {
            val iterator = times.entries.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        return true
    }
}
