package com.xiyunmn.cwmhook.core.runtime

import android.os.SystemClock

/**
 * Tracks host task scheduling and synchronous native-network work so optional module work can
 * yield to content loading. This is deliberately state-only; it does not install hooks itself.
 */
object HostLoadActivityTracker {
    private val lock = Any()
    private var activeNetworkCalls = 0
    private var lastActivityElapsedMs = SystemClock.elapsedRealtime()

    fun onTaskScheduled() {
        synchronized(lock) {
            lastActivityElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    fun onNetworkCallStarted() {
        synchronized(lock) {
            activeNetworkCalls += 1
            lastActivityElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    fun onNetworkCallFinished() {
        synchronized(lock) {
            activeNetworkCalls = (activeNetworkCalls - 1).coerceAtLeast(0)
            lastActivityElapsedMs = SystemClock.elapsedRealtime()
        }
    }

    fun remainingQuietDelayMs(
        quietPeriodMs: Long,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): Long {
        return synchronized(lock) {
            if (activeNetworkCalls > 0) {
                quietPeriodMs
            } else {
                (lastActivityElapsedMs + quietPeriodMs - nowElapsedMs).coerceAtLeast(0L)
            }
        }
    }
}
