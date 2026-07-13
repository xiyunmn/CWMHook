package com.xiyunmn.cwmhook.core.runtime

import android.os.Handler
import android.view.View
import com.xiyunmn.cwmhook.core.XposedCompat
import java.util.IdentityHashMap

object ModuleViewTaskRegistry {
    private val lock = Any()
    private val cancellations = IdentityHashMap<Runnable, () -> Unit>()

    fun post(view: View, delayMillis: Long = 0L, action: () -> Unit): Boolean {
        lateinit var task: Runnable
        task = Runnable {
            unregister(task)
            if (!XposedCompat.isRetiring()) {
                action()
            }
        }
        register(task) { view.removeCallbacks(task) }
        val accepted = if (delayMillis > 0L) {
            view.postDelayed(task, delayMillis)
        } else {
            view.post(task)
        }
        if (!accepted) {
            unregister(task)
        }
        return accepted
    }

    fun postOnAnimation(view: View, action: () -> Unit) {
        lateinit var task: Runnable
        task = Runnable {
            unregister(task)
            if (!XposedCompat.isRetiring()) {
                action()
            }
        }
        register(task) { view.removeCallbacks(task) }
        view.postOnAnimation(task)
    }

    fun track(handler: Handler, runnable: Runnable) {
        register(runnable) { handler.removeCallbacks(runnable) }
    }

    fun untrack(runnable: Runnable) {
        unregister(runnable)
    }

    fun cancelAll() {
        val pending = synchronized(lock) {
            cancellations.values.toList().also { cancellations.clear() }
        }
        pending.forEach { cancel -> runCatching(cancel) }
    }

    private fun register(runnable: Runnable, cancellation: () -> Unit) {
        synchronized(lock) {
            cancellations[runnable] = cancellation
        }
    }

    private fun unregister(runnable: Runnable) {
        synchronized(lock) {
            cancellations.remove(runnable)
        }
    }
}
