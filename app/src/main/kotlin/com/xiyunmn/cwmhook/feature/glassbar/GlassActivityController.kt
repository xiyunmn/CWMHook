package com.xiyunmn.cwmhook.feature.glassbar

import android.app.Activity
import android.view.MotionEvent
import android.view.ViewTreeObserver
import com.xiyunmn.cwmhook.config.glassbar.GlassBarsConfig
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger

internal class GlassActivityController(
    private val activity: Activity,
    private val kind: GlassBarKind,
) : AutoCloseable {
    private val root = activity.window.decorView
    private var config = GlassBarsConfig()
    private var session: GlassBarSession? = null
    private var attempts = 0
    private var focused = activity.hasWindowFocus()
    private var suspended = false
    private var closed = false
    private val suspendSession = Runnable {
        session?.close()
        session = null
    }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (!closed && !suspended && (session == null || session?.closed == true) && attempts < 16) bind()
    }
    private val observer = root.viewTreeObserver.also { it.addOnGlobalLayoutListener(layoutListener) }

    fun apply(config: GlassBarsConfig) {
        if (closed) return
        this.config = config
        attempts = 0
        suspended = false
        root.removeCallbacks(suspendSession)
        if (session?.closed == true) {
            session?.close()
            session = null
        }
        if (session == null) bind() else session?.update(config)
    }

    fun setActive(active: Boolean) {
        focused = active
        session?.setActive(active)
        if (active && !suspended && (session == null || session?.closed == true)) {
            attempts = 0
            bind()
        }
    }

    fun dispatchTouch(event: MotionEvent, dispatchNative: (MotionEvent) -> Boolean): Boolean =
        session?.dispatchTouch(event, dispatchNative) ?: dispatchNative(event)

    private fun bind() {
        if (closed || suspended || activity.isFinishing || activity.isDestroyed || !kind.enabled(config)) return
        if (session?.closed == true) {
            session?.close()
            session = null
        }
        attempts++
        val binding = GlassBarBindingResolver.resolve(kind, root) ?: return
        runCatching {
            val next = GlassBarSession(binding, ::suspend)
            session = next
            next.attach(config, focused)
            ModuleFileLogger.i(TAG, "Glass bar attached: kind=$kind activity=${activity.javaClass.simpleName}")
        }.onFailure(::suspend)
    }

    private fun suspend(error: Throwable) {
        if (closed || suspended) return
        suspended = true
        ModuleFileLogger.w(TAG, "Glass bar layout unavailable: kind=$kind", error)
        root.post(suspendSession)
    }

    override fun close() {
        if (closed) return
        closed = true
        root.removeCallbacks(suspendSession)
        if (observer.isAlive) observer.removeOnGlobalLayoutListener(layoutListener)
        session?.close()
        session = null
    }

    private companion object { const val TAG = "CWMHook.GlassBars" }
}
