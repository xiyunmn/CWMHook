package com.xiyunmn.cwmhook.core.logging

import android.os.Process
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object ModuleLogLineFormatter {
    fun format(
        priority: Int,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val priorityName = when (priority) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "A"
            else -> priority.toString()
        }
        val base = "$time ${Process.myPid()}/${Thread.currentThread().id} $priorityName/$tag: $message"
        return if (throwable == null) {
            base
        } else {
            base + '\n' + Log.getStackTraceString(throwable)
        }
    }
}
