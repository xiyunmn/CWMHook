package com.xiyunmn.cwmhook.feature.statusbar

import android.app.Activity
import android.view.Window
import java.util.WeakHashMap

internal class StatusBarTransientOverlayRegistry(
    private val windowRegistry: StatusBarWindowRegistry,
) {
    private val visibleActivities = WeakHashMap<Activity, Boolean>()

    fun setVisible(activity: Activity, visible: Boolean) {
        synchronized(visibleActivities) {
            if (visible) {
                visibleActivities[activity] = true
            } else {
                visibleActivities.remove(activity)
            }
        }
    }

    fun isVisible(window: Window): Boolean {
        val activity = windowRegistry.findActivityForWindow(window, window.decorView) ?: return false
        return synchronized(visibleActivities) {
            visibleActivities[activity] == true
        }
    }
}
