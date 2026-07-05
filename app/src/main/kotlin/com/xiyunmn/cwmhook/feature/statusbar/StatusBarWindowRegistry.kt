package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import java.util.WeakHashMap

internal class StatusBarWindowRegistry(
    private val cacheKeys: StatusBarCacheKeys,
    private val maxSceneCacheSize: Int,
) {
    private val windowStates = WeakHashMap<Window, StatusBarWindowState>()
    private val windowActivities = WeakHashMap<Window, Activity>()
    private var foregroundWindow: Window? = null

    fun state(window: Window): StatusBarWindowState {
        synchronized(windowStates) {
            return windowStates.getOrPut(window) { StatusBarWindowState(cacheKeys::cacheKey, maxSceneCacheSize) }
        }
    }

    fun setForegroundWindow(window: Window) {
        synchronized(windowStates) {
            foregroundWindow = window
        }
    }

    fun clearForegroundWindow(window: Window) {
        synchronized(windowStates) {
            if (foregroundWindow === window) {
                foregroundWindow = null
            }
        }
    }

    fun isForegroundWindow(window: Window, state: StatusBarWindowState): Boolean {
        val current = synchronized(windowStates) { foregroundWindow }
        return if (current != null) {
            current === window
        } else {
            !state.everFocused || state.hasFocus
        }
    }

    fun foregroundWindows(): List<Window> {
        return synchronized(windowActivities) {
            windowActivities.keys.filter { window ->
                isForegroundWindow(window, state(window))
            }
        }
    }

    fun rememberActivityWindow(activity: Activity) {
        synchronized(windowActivities) {
            windowActivities[activity.window] = activity
        }
    }

    fun findActivityForWindow(window: Window, decorView: View? = null): Activity? {
        synchronized(windowActivities) {
            windowActivities[window]?.let { return it }
        }
        return findActivity(decorView?.context ?: window.context)
    }

    fun findActivity(context: Context?): Activity? {
        var current = context
        repeat(12) {
            when (current) {
                is Activity -> return current
                is ContextWrapper -> current = current.baseContext
                else -> return null
            }
        }
        return null
    }
}
