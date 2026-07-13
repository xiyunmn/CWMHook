package com.xiyunmn.cwmhook.ui.settings

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import com.xiyunmn.cwmhook.core.logging.ModuleFileLogger
import com.xiyunmn.cwmhook.core.runtime.ModuleViewTaskRegistry
import com.xiyunmn.cwmhook.ui.common.PanelTheme
import java.util.WeakHashMap

object ModuleSettingsPageWindow {
    private const val TAG = "CWMHook.SettingsPage"
    private const val RESTORE_TTL_MS = 60_000L
    private val activeWindows = WeakHashMap<Activity, Holder>()
    private var pendingRestore: PendingRestore? = null

    interface BackHandler {
        fun handleBack(): Boolean
    }

    interface RestorablePage : BackHandler {
        fun captureRestoreState(): Any?
    }

    fun show(
        activity: Activity,
        createPage: (Activity, FrameLayout, PanelTheme, Any?) -> View,
        restoreState: Any? = null,
        onShown: (Activity) -> Unit,
        onReused: (Activity) -> Unit,
        onClosed: (String) -> Unit,
    ) {
        val existing = activeWindows[activity]?.takeIf { it.dialog.isShowing }
        if (existing != null) {
            refreshHolder(existing, "show:reuse")
            pendingRestore = null
            existing.overlay.visibility = View.VISIBLE
            existing.overlay.bringToFront()
            existing.overlay.requestFocus()
            onReused(activity)
            return
        }
        if (restoreState == null) {
            pendingRestore = null
        }

        val theme = PanelTheme.from(activity)
        val dialog = Dialog(activity, android.R.style.Theme_Material_NoActionBar).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(false)
        }
        val overlay = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            alpha = 0f
            setBackgroundColor(theme.panelBackground)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    handleBack(this, onClosed)
                    true
                } else {
                    false
                }
            }
        }
        val page = createPage(activity, overlay, theme, restoreState)
        overlay.addView(page, frameMatch())
        val holder = Holder(activity, dialog, overlay, createPage, onClosed)
        activeWindows[activity] = holder
        dialog.setContentView(overlay, frameMatch())
        dialog.setOnDismissListener {
            if (activeWindows[activity] === holder) {
                activeWindows.remove(activity)
            }
            if (!holder.closed) {
                holder.closed = true
                holder.onClosed(holder.closeReason)
            }
        }
        dialog.show()
        configureWindow(dialog.window, theme)
        overlay.requestFocus()
        overlay.animate()
            .alpha(1f)
            .setDuration(180L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()
        onShown(activity)
    }

    fun refreshActiveThemes(reason: String) {
        val holders = activeWindows.values.toList()
        holders.forEach { holder ->
            if (holder.dialog.isShowing) {
                ModuleViewTaskRegistry.post(holder.overlay) {
                    if (activeWindows[holder.activity] === holder && holder.dialog.isShowing) {
                        refreshHolder(holder, reason)
                    }
                }
            }
        }
    }

    fun captureActiveForHostChange(reason: String) {
        val holder = activeWindows.values.firstOrNull { it.dialog.isShowing } ?: return
        pendingRestore = PendingRestore(
            activityClassName = holder.activity.javaClass.name,
            state = holder.captureState(),
            createdAtMillis = System.currentTimeMillis(),
        )
        ModuleFileLogger.i(TAG, "Module settings page restore state captured: reason=$reason activity=${holder.activity.javaClass.name}")
    }

    fun handleHostActivityDestroy(activity: Activity, reason: String) {
        val holder = activeWindows[activity] ?: return
        if (holder.dialog.isShowing) {
            pendingRestore = PendingRestore(
                activityClassName = activity.javaClass.name,
                state = holder.captureState(),
                createdAtMillis = System.currentTimeMillis(),
            )
            ModuleFileLogger.i(TAG, "Module settings page detached from destroyed host: reason=$reason activity=${activity.javaClass.name}")
        }
        activeWindows.remove(activity)
        holder.detach()
    }

    fun restoreIfNeeded(
        activity: Activity,
        createPage: (Activity, FrameLayout, PanelTheme, Any?) -> View,
        reason: String,
        onShown: (Activity) -> Unit,
        onReused: (Activity) -> Unit,
        onClosed: (String) -> Unit,
    ): Boolean {
        val state = pendingRestore ?: return false
        if (hasPanel(activity)) {
            pendingRestore = null
            return false
        }
        if (!state.isFresh() || state.activityClassName != activity.javaClass.name) {
            if (!state.isFresh()) {
                pendingRestore = null
            }
            return false
        }
        pendingRestore = null
        ModuleViewTaskRegistry.post(activity.window.decorView, 80L) {
                if (!activity.isFinishing && !activity.isDestroyedCompat()) {
                    show(
                        activity = activity,
                        createPage = createPage,
                        restoreState = state.state,
                        onShown = onShown,
                        onReused = onReused,
                        onClosed = onClosed,
                    )
                    ModuleFileLogger.i(TAG, "Module settings page restored: reason=$reason activity=${activity.javaClass.name}")
                }
            }
        return true
    }

    fun hasPanel(activity: Activity): Boolean {
        return activeWindows[activity]?.dialog?.isShowing == true
    }

    fun closeExisting(activity: Activity, reason: String, onClosed: (String) -> Unit): Boolean {
        val holder = activeWindows[activity]?.takeIf { it.dialog.isShowing } ?: return false
        val page = holder.overlay.getChildAt(0) as? BackHandler
        if (page?.handleBack() == true) {
            return true
        }
        holder.onClosed = onClosed
        close(holder.overlay, reason, onClosed)
        return true
    }

    fun detachAll(reason: String) {
        val holders = activeWindows.values.toList()
        activeWindows.clear()
        pendingRestore = null
        holders.forEach { holder ->
            holder.closeReason = reason
            holder.detach()
        }
    }

    fun close(overlay: View, reason: String, onClosed: (String) -> Unit) {
        val holder = activeWindows.values.firstOrNull { it.overlay === overlay }
        if (holder != null) {
            pendingRestore = null
        }
        overlay.animate()
            .alpha(0f)
            .setDuration(160L)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                if (holder != null) {
                    holder.closed = true
                    holder.closeReason = reason
                    if (holder.dialog.isShowing) {
                        holder.dialog.dismiss()
                    }
                    onClosed(reason)
                } else {
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                    onClosed(reason)
                }
            }
            .start()
    }

    fun isBackKey(event: KeyEvent?): Boolean {
        return event?.keyCode == KeyEvent.KEYCODE_BACK
    }

    private fun handleBack(overlay: FrameLayout, onClosed: (String) -> Unit) {
        val page = overlay.getChildAt(0) as? BackHandler
        if (page?.handleBack() == true) {
            return
        }
        close(overlay, "viewKey", onClosed)
    }

    private fun refreshHolder(holder: Holder, reason: String) {
        val theme = PanelTheme.from(holder.activity)
        val state = holder.captureState()
        holder.overlay.animate().cancel()
        holder.overlay.alpha = 1f
        holder.overlay.setBackgroundColor(theme.panelBackground)
        holder.overlay.removeAllViews()
        holder.overlay.addView(holder.createPage(holder.activity, holder.overlay, theme, state), frameMatch())
        configureWindow(holder.dialog.window, theme)
        holder.overlay.requestFocus()
        ModuleFileLogger.i(TAG, "Module settings page theme refreshed: reason=$reason activity=${holder.activity.javaClass.name}")
    }

    private fun configureWindow(window: Window?, theme: PanelTheme) {
        window ?: return
        window.setBackgroundDrawable(ColorDrawable(theme.panelBackground))
        window.setDimAmount(0f)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = theme.rowBackground
        window.navigationBarColor = theme.rowBackground
        val appearance = systemBarAppearance(theme.rowBackground)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true)
            window.insetsController?.setSystemBarsAppearance(
                appearance,
                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = legacySystemUiFlags(theme.rowBackground)
        }
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun systemBarAppearance(color: Int): Int {
        return if (isLightColor(color)) {
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        } else {
            0
        }
    }

    @Suppress("DEPRECATION")
    private fun legacySystemUiFlags(color: Int): Int {
        return if (isLightColor(color)) {
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        } else {
            0
        }
    }

    private fun isLightColor(color: Int): Boolean {
        val luminance = 0.299 * android.graphics.Color.red(color) +
            0.587 * android.graphics.Color.green(color) +
            0.114 * android.graphics.Color.blue(color)
        return luminance > 180.0
    }

    private fun frameMatch(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    private fun Activity.isDestroyedCompat(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && isDestroyed
    }

    private class Holder(
        val activity: Activity,
        val dialog: Dialog,
        val overlay: FrameLayout,
        val createPage: (Activity, FrameLayout, PanelTheme, Any?) -> View,
        var onClosed: (String) -> Unit,
    ) {
        var closed: Boolean = false
        var closeReason: String = "dismiss"

        fun captureState(): Any? {
            return (overlay.getChildAt(0) as? RestorablePage)?.captureRestoreState()
        }

        fun detach() {
            dialog.setOnDismissListener(null)
            overlay.animate().cancel()
            runCatching {
                if (dialog.isShowing) {
                    dialog.dismiss()
                }
            }.onFailure { throwable ->
                ModuleFileLogger.w(TAG, "Failed to detach module settings page", throwable)
            }
            closed = true
        }
    }

    private data class PendingRestore(
        val activityClassName: String,
        val state: Any?,
        val createdAtMillis: Long,
    ) {
        fun isFresh(): Boolean {
            return System.currentTimeMillis() - createdAtMillis <= RESTORE_TTL_MS
        }
    }
}
