package com.xiyunmn.cwmhook.core.runtime

import android.app.Activity
import android.app.Application
import java.lang.reflect.Field

object HostProcessInspector {
    fun currentApplication(): Application? {
        return runCatching {
            val activityThread = Class.forName("android.app.ActivityThread")
            activityThread.getDeclaredMethod("currentApplication").invoke(null) as? Application
        }.getOrNull()
    }

    fun currentActivity(): Activity? {
        return activityRecords()
            .let { records -> records.firstOrNull { !it.paused }?.activity ?: records.firstOrNull()?.activity }
    }

    fun activities(): List<Activity> {
        return activityRecords().map { it.activity }.distinctBy { System.identityHashCode(it) }
    }

    private fun activityRecords(): List<ActivityRecord> {
        return runCatching {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val thread = activityThreadClass.getDeclaredMethod("currentActivityThread").invoke(null)
                ?: return@runCatching emptyList()
            val activitiesField = activityThreadClass.getDeclaredField("mActivities").also { it.isAccessible = true }
            val records = (activitiesField.get(thread) as? Map<*, *>)?.values.orEmpty()
            records.mapNotNull { record -> record?.toActivityRecord() }
        }.getOrDefault(emptyList())
    }

    private fun Any.toActivityRecord(): ActivityRecord? {
        val activity = findField(javaClass, "activity")?.get(this) as? Activity ?: return null
        val paused = (findField(javaClass, "paused")?.get(this) as? Boolean) ?: false
        return ActivityRecord(activity, paused)
    }

    private fun findField(type: Class<*>, name: String): Field? {
        var current: Class<*>? = type
        while (current != null) {
            runCatching { current.getDeclaredField(name).also { it.isAccessible = true } }
                .getOrNull()
                ?.let { return it }
            current = current.superclass
        }
        return null
    }

    private data class ActivityRecord(
        val activity: Activity,
        val paused: Boolean,
    )
}
